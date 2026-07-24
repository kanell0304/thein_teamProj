# API 테스트 가이드

`backend/http/*.http` 파일들을 IntelliJ HTTP Client로 실행할 때 어떤 순서로, 어떤 선행 조건을 채우고
실행해야 하는지 정리한 문서. **새 기능을 만들면서 `.http` 파일을 추가/수정했다면 이 문서도 같이 갱신한다**
(맨 아래 "새 테스트 파일을 추가할 때" 참고).

## 0. 서버 실행 전제 조건

- PostgreSQL이 떠 있고 `application.properties`의 `spring.datasource.*`와 일치해야 한다.
- `backend/.env`에 최소한 `JWT_SECRET`, `KAKAO_REST_API_KEY`가 있어야 로그인/카카오 검색이 동작한다.
  추천 기능까지 테스트하려면 `OPENAI_API_KEY`도 필요하고, 이미지 정확도를 보려면 `GOOGLE_PLACES_API_KEY`도
  있으면 좋다(없으면 카카오 이미지 검색으로 자동 폴백되어 에러는 안 남).
- `dev-login.http`(아래 1번)는 `spring.profiles.active`에 `dev`가 포함돼 있어야 동작한다
  (`DevAuthController`가 `@Profile("dev")`로 막혀 있음). 로컬 기본값은 `dev`라서 별도 설정 없이 되지만,
  다른 프로필로 띄운 서버에서는 이 방식으로 토큰을 못 받는다 — 이 경우 실제 카카오 로그인 플로우로
  토큰을 발급받아야 한다(아직 `.http`로 자동화되어 있지 않음).
- `./gradlew bootRun`으로 띄우면 `http://localhost:8081`에서 서비스된다.

## 1. 실행 순서(의존 관계)

모든 기능 테스트는 **인증 토큰 발급이 0순위**다. 그 다음은 기능 성격에 따라 채팅방/모임이 먼저 있어야
하는 계층 구조를 따른다.

```
dev-login.http (1~5번 요청)
  └─ {{authToken}}, {{memberId}} (1번) / {{authToken2}}, {{memberId2}} (2번) / {{memberId3}}~{{memberId5}} (3~5번)
       │  personalOptions[].participantId가 실제 회원 ID(Long)라서, 참여자가 여럿 등장하는 시나리오를
       │  테스트하려면 필요한 만큼 미리 만들어둬야 한다. 2명이면 1~2번, 5인 시나리오면 1~5번까지 실행.
       │
       ├─ chat.http
       │    └─ {{chatRoomId}} 발급 (+ 채팅방 CRUD, 메시지 이력 조회 테스트)
       │
       ├─ meetup-flow.http (내부 0번 요청이 자체적으로 채팅방을 만듦 - chat.http 불필요, 1~2번 회원만 있으면 됨)
       │    └─ {{chatRoomId}}, {{meetupId}}, {{roundId}}, {{firstCandidateId}}, {{secondCandidateId}},
       │       {{expiredMeetupId}}, {{expiredRoundId}}, {{expiredCandidateId}} 발급
       │    └─ 모임 생성 → 추천 회차 생성(실제 OpenAI 호출, 이 시점에 개인 선호가 DB에 저장됨) → 투표 →
       │       조회 → 재추천(선호 갱신 확인) → 최종확정 → 최종공지 수정(변경이력) → 투표마감시간 동작까지
       │       엔드투엔드 흐름 전체를 한 번에 검증
       │
       └─ recommendation-scenarios.http (chat.http 실행 후 {{chatRoomId}} 필요, 5인 시나리오는 1~5번 회원 필요)
            └─ POST /api/meetups/{meetupId}/rounds 하나를 다양한 입력값으로 검증
            └─ 시나리오별로 destination/시간/목적이 다르면 모임도 따로 만들어야 해서
               파일 안에 그룹(A~E)별 "모임 생성" 블록이 섞여 있음 - 그룹 안내를 보고 실행
```

웹소켓(STOMP)은 IntelliJ HTTP Client로 테스트할 수 없다. `backend/websocket-test/chat-test.html`을
브라우저로 열어서(파일을 직접 열면 됨) `{{authToken}}`과 방 ID를 넣고 확인한다 — 채팅 메시지뿐 아니라
추천 진행상황(`recommendation-progress`), 투표 집계(`vote-updates`), 최종공지(`final-notice`)까지
같은 화면에서 실시간으로 뜬다. 두 개의 탭(또는 시크릿 창)에 서로 다른 토큰으로 접속하면 혼자서도
여러 사용자가 동시에 참여하는 상황을 재현할 수 있다.

## 2. 파일별 요약

| 파일 | 선행 조건(필요한 전역 변수) | 이 파일이 발급하는 변수 | 비고 |
|---|---|---|---|
| `dev-login.http` | 없음 | `{{authToken}}`, `{{memberId}}` (1번), `{{authToken2}}`, `{{memberId2}}` (2번), `{{memberId3}}`~`{{memberId5}}` (3~5번) | 항상 제일 먼저 실행. 회원마다 kakaoId가 달라 별도 회원이 생성된다. dev 프로필 전용. |
| `chat.http` | `{{authToken}}` | `{{chatRoomId}}` | 채팅방 생성/참여/메시지 이력 조회. 실시간 송수신은 `chat-test.html`로. |
| `meetup-flow.http` | `{{authToken}}`, `{{memberId}}`, `{{memberId2}}` | `{{chatRoomId}}`, `{{meetupId}}`, `{{roundId}}`, `{{firstCandidateId}}`, `{{secondCandidateId}}`, `{{expiredMeetupId}}`, `{{expiredRoundId}}`, `{{expiredCandidateId}}` | 모임→추천(개인선호 저장)→투표→조회→재추천(선호 갱신)→확정→최종공지 수정→투표마감시간까지 전체 흐름 1회 검증용. 실제 OpenAI 호출 3회 포함(회차당 1~2분). |
| `recommendation-scenarios.http` | `{{authToken}}`, `{{chatRoomId}}`(chat.http 실행 필요), 5인 시나리오는 `{{memberId}}`~`{{memberId5}}` 전부 필요 | `{{meetupIdA}}` ~ `{{meetupIdE}}` | 추천 엔드포인트 하나를 다양한 입력값(참여자 수, 예산 편차, 제외 음식 등)으로 검증. "전체 실행" 금지 — 블록당 실제 OpenAI 호출. |
| `websocket-test/chat-test.html` | `{{authToken}}`(dev-login.http에서 복사) | - | 브라우저로 직접 열어서 실시간 채팅/추천진행상황/투표/최종공지 웹소켓 이벤트를 눈으로 확인. `.http` 파일이 아니라 별도 실행. |

## 3. 새 테스트 파일을 추가할 때

새 기능을 만들고 그 기능을 테스트하는 `.http` 파일(또는 브라우저 테스트 페이지)을 추가했다면, 이 문서에
아래 내용을 반영한다.

1. **1. 실행 순서(의존 관계)** 다이어그램에 새 파일이 어느 계층에 들어가는지 추가(어떤 파일 실행 후에
   실행해야 하는지, 즉 어떤 전역 변수를 소비하는지).
2. **2. 파일별 요약** 표에 행을 추가: 선행 조건(필요 변수), 이 파일이 새로 발급하는 변수, 특이사항
   (실제 외부 API 호출이 있는지, 전체 실행하면 안 되는지, 다중 사용자 토큰이 필요한지 등).
3. 만약 새 파일이 기존 파일이 만드는 전역 변수(`{{chatRoomId}}`, `{{meetupId}}` 등)를 그대로 재사용한다면
   그 사실을 파일 맨 위 안내 주석에도 남긴다(기존 파일들의 관례를 따름).

<!-- CI/CD 배포 파이프라인 테스트 -->
