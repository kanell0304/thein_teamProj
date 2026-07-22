# 모먹지 DB 설계 (초안)

> 실제 구현·테스트된 AI 추천 기능(`backend/src/main/java/com/anything/momeogji/service/recommendation`)의 API 스펙을 기준으로 작성했습니다. 채팅/모임 흐름은 아직 구현 전이라 v3 기획서 기준으로 잡아둔 스켈레톤입니다.
>
> ERD 이미지: [db-erd.svg](db-erd.svg)

## 전체 구조

```
USERS(Member) ─┬─ CHAT_ROOM_MEMBERS ─ CHAT_ROOMS ─┬─ CHAT_MESSAGES
                ├─ MEETUPS(주최) ──────────────────┘
                ├─ MEETUP_PARTICIPANTS ─┬─ PARTICIPANT_PREFERENCES
                │                       ├─ MYDATA_CONSENTS
                │                       └─ VOTES ── ROUND_CANDIDATES ── RESTAURANTS
                └─ FINAL_NOTICE_CHANGE_LOGS

MEETUPS ─┬─ RECOMMENDATION_ROUNDS ─ ROUND_CANDIDATES
         └─ FINAL_NOTICES
```

## 테이블별 컬럼 설명

### USERS — 사용자 (`entity/Member.java`, 카카오 로그인 전용)
> 일반 회원가입/로그인은 사용하지 않고 카카오 로그인만 사용하기로 확정. `kakao_id`가 사실상 유일한 계정 식별자다. 실제 OAuth 플로우(인가 코드 교환, 토큰 발급/검증)는 아직 미구현이고, 스키마/엔티티만 먼저 확정했다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | 사용자 고유 식별자 |
| kakao_id | VARCHAR UK, NOT NULL | 카카오 계정 식별자. 로그인 시 이 값으로 기존 회원 조회/신규 가입 판단 |
| nickname | VARCHAR NOT NULL | 카카오 프로필에서 받아오는 닉네임 |
| profile_image_url | VARCHAR (nullable) | 카카오 프로필 이미지 URL |
| role | VARCHAR(USER/ADMIN) | 인가(authorization)용 권한. 기본값 USER |
| created_at | DATETIME | 최초 가입(첫 로그인) 시각. 자동 기록 |
| updated_at | DATETIME | 정보 갱신 시각. 자동 기록 |

### CHAT_ROOMS — 채팅방

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | 채팅방 식별자 |
| name | VARCHAR | 채팅방 이름 |

### CHAT_ROOM_MEMBERS — 채팅방 참여자 (users ↔ chat_rooms 다대다)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| chat_room_id | BIGINT FK | 소속 채팅방 |
| user_id | BIGINT FK | 참여 사용자 |

### CHAT_MESSAGES — 채팅 메시지

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| chat_room_id | BIGINT FK | 어느 채팅방의 메시지인지 |
| user_id | BIGINT FK | 작성자 (시스템 메시지는 NULL 허용) |
| content | TEXT | 메시지 본문 |
| created_at | DATETIME | 메시지가 기록된 시각(년-월-일-시-분-초) |

### MEETUPS — 모임(음식점 결정 세션 단위)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| chat_room_id | BIGINT FK | 이 모임이 시작된 채팅방 |
| host_user_id | BIGINT FK | 모임을 만든 주최자 |
| status | VARCHAR | 모임 진행 상태 (DRAFT/RECOMMENDING/VOTING/FINALIZED 등) |
| destination_name | VARCHAR | 목적지 이름 — `CommonOptionRequest.destinationName` |
| destination_latitude | DECIMAL(10,7) | 목적지 위도 — 이 좌표 기준으로 음식점 검색 |
| destination_longitude | DECIMAL(10,7) | 목적지 경도 |
| meeting_time | DATETIME | 약속 일시 — `CommonOptionRequest.meetingTime` |
| purpose | VARCHAR | 모임 목적 (자유 텍스트, 예: "식사", "술자리") |
| vote_deadline_at | DATETIME | 투표 마감 시각 |

### MEETUP_PARTICIPANTS — 모임 참여자 (주최자가 초대한 대상별 진행 상태)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| meetup_id | BIGINT FK | 소속 모임 |
| user_id | BIGINT FK | 참여자 |
| submission_status | VARCHAR | 개인 옵션 제출 상태 (제출완료/미응답/참여안함) |
| confirmed_for_ai | BOOLEAN | AI 추천 요청 시 실제로 포함할 참여자로 확정됐는지 |

### PARTICIPANT_PREFERENCES — 개인 옵션 (`PersonalOptionRequest`와 1:1 매칭, 실제 구현됨)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| meetup_participant_id | BIGINT FK | 어느 참여자의 옵션인지 (1:1) |
| walk_minutes | INT | 도보 가능 시간(분) — `walkMinutes`. 참여자 평균으로 검색 반경 계산 |
| preferred_categories | JSONB | 선호 카테고리 배열 — `preferredCategories`. 전원 합산해 카테고리 우선순위로 집계 |
| budget_limit | INT (nullable) | 1인당 예산 상한(원) — `budgetLimit`. NULL이면 무제한 |
| parking_needed | BOOLEAN | 주차 필요 여부 — `parkingNeeded` |
| excluded_foods | JSONB | 제외 음식 배열 — `excludedFoods`. 한 명이라도 넣으면 후보에서 제외 |
| atmosphere | VARCHAR (nullable) | 선호 분위기 — `atmosphere`. NULL이면 상관없음 |

### MYDATA_CONSENTS — 마이데이터 동의 (아직 미구현, v3 기획 반영용 스켈레톤)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| meetup_participant_id | BIGINT FK | 어느 참여자의 동의인지 (1:1) |
| consent_status | VARCHAR | 동의 여부 (AGREED/DECLINED) |
| processing_status | VARCHAR | 목업 데이터 비동기 가공 상태 (대기/처리중/완료/실패) |
| processed_result | TEXT | 가공된 결과. 개인 원본은 노출 안 하고 그룹 조건으로만 사용 |

### RESTAURANTS — 음식점 마스터 (카카오 검색 결과 캐시, `RestaurantCandidate`와 매칭)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | 내부 식별자 |
| kakao_place_id | VARCHAR UK, NOT NULL | 카카오 장소 고유 id — `RestaurantCandidate.id`. 중복 저장 방지 키 |
| name | VARCHAR | 상호명 |
| category | VARCHAR | 카테고리 (카카오 `category_group_name`) |
| road_address | VARCHAR | 도로명 주소 |
| address | VARCHAR | 지번 주소 |
| latitude | DECIMAL(10,7) | 위도 |
| longitude | DECIMAL(10,7) | 경도 |

### RECOMMENDATION_ROUNDS — 추천 회차 (재추천마다 1건씩 생성)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| meetup_id | BIGINT FK | 소속 모임 |
| round_no | INT | 회차 번호(1, 2, 3…) — 재추천마다 증가 |
| status | VARCHAR | PENDING/COMPLETED/FAILED — 추후 웹소켓으로 진행 상태를 알릴 때 사용 |
| preference_note | TEXT (nullable) | 재추천 시 직접 입력한 우선순위 — `preferenceNote` |

### ROUND_CANDIDATES — 회차별로 AI가 고른 음식점 (회차당 3건)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| round_id | BIGINT FK | 어느 회차의 후보인지 |
| restaurant_id | BIGINT FK | 어느 음식점인지 |
| rank_no | INT | 추천 순위(1~3). `rank`는 PostgreSQL 예약어라 `rank_no`로 이름 변경 |
| distance_meters | INT (nullable) | 목적지로부터 거리(m) — 카카오 검색 결과의 `distanceMeters` |
| reason | TEXT | AI가 작성한 추천 이유 |

### VOTES — 투표

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| round_candidate_id | BIGINT FK | 어느 후보에 투표했는지 |
| meetup_participant_id | BIGINT FK | 누가 투표했는지 |
| voted_at | DATETIME | 투표 시각 |
| — | UNIQUE(round_candidate_id, meetup_participant_id) | 같은 후보 중복 투표 방지 (다른 후보에는 각각 투표 가능) |

### FINAL_NOTICES — 최종 공지

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| meetup_id | BIGINT FK | 소속 모임 (모임당 1건, UNIQUE) |
| restaurant_id | BIGINT FK | 확정된 음식점 |
| meeting_datetime | DATETIME | 약속 일시 |
| pinned_until | DATETIME | 채팅방 상단 고정 해제 시각(=약속 시각) |

### FINAL_NOTICE_CHANGE_LOGS — 최종 공지 수정 이력

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| final_notice_id | BIGINT FK | 어느 공지의 수정 이력인지 |
| changed_by | BIGINT FK | 수정한 사용자(주최자) |
| changed_field | VARCHAR | 변경된 필드명 |
| changed_at | DATETIME | 변경 시각 |

## 알려진 참고 사항

- `votes`는 설계대로 `round_candidate_id` 기준으로 영속화되어 있습니다(`POST/DELETE /api/meetups/{meetupId}/rounds/{roundId}/candidates/{roundCandidateId}/votes`). 참여자별로 idempotent하게 처리되며, 득표수는 매 투표마다 웹소켓으로 실시간 브로드캐스트됩니다.
- `participant_preferences`도 실제로 저장됩니다. `personalOptions[].participantId`는 자유 문자열이 아니라 **실제 회원 ID(Long)**여야 하며, 채팅방 멤버십까지는 요구하지 않고 실존하는 회원인지만 확인합니다(투표는 기존처럼 채팅방 멤버십을 요구함). 재추천 시 같은 참여자가 다시 제출하면 최신값으로 갱신됩니다.
- 재추천 시 "이전에 추천된 곳 제외" 목록은 별도 컬럼 없이, 같은 `meetup_id`의 이전 `round_candidates`를 조회해서 파생합니다.
- `final_notice_change_logs`도 실제로 쓰입니다(`PATCH /api/meetups/{meetupId}/final-notice`로 약속시간 수정 시 호스트/변경필드/시각을 기록). 지금은 약속시간(`meeting_datetime`)만 수정 가능합니다.
- `meetups.vote_deadline_at`도 실제로 적용됩니다. 모임 생성 시 선택적으로 지정하며, 지나면 투표/투표취소 모두 400으로 막힙니다.
- `mydata_consents`는 아직 기능 자체가 구현되지 않아 v3 기획서 기준 스켈레톤 그대로입니다(v3 범위 외).
- 로그인은 카카오 전용으로 확정되어 실제로 구현되어 있습니다(카카오 OAuth + 자체 JWT 발급). `SecurityConfig`가 `/api/**`를 기본적으로 인증 필요로 막고 있고, `/api/dev/**`(dev-login 우회)는 `dev` 스프링 프로필에서만 노출됩니다.
