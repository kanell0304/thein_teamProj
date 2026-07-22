# 오늘 모 먹지? Frontend

모바일 메신저형 채팅방에서 대화와 모임 조건을 바탕으로 AI가 가게를 추천하고, 참가자 투표로 최종 장소를 결정하는 독립 React 웹 프로젝트입니다.

## 실행 방법

```bash
cd frontend
npm install
npm run dev
```

기본 개발 주소는 `http://127.0.0.1:5173/`입니다.

```bash
npm run lint
npm run build
```

## 주요 화면 흐름

1. 로그인 후 친구·채팅 목록에서 참여할 방 선택
2. 채팅방 하단의 **오늘 모 먹지?** 아이콘 선택
3. 8단계 모임 조건 입력
   - 날짜
   - 시간(24시간제, 현재 시간과 가까운 값 기본 선택)
   - 장소
   - 참가자
   - 모임 주제
   - 대화에서 추출한 메뉴
   - 피하고 싶은 음식
   - 원하는 분위기
4. 입력값을 백엔드 AI 추천 API로 전달
5. 추천 가게 3곳과 `재추천`을 포함한 투표 생성
6. 선택된 참가자에게만 공지와 투표 화면 표시
7. 투표 종료 후 최종 가게 결과 표시

메뉴·피할 음식·분위기는 여러 개를 선택할 수 있으며, 직접 입력한 값은 **추가** 버튼으로 선택 항목에 넣을 수 있습니다.

## 투표 규칙

- 한 라운드의 선택지는 `가게 3곳 + 재추천`으로 총 4개입니다.
- 참가자는 한 라운드에 가게 3곳과 재추천을 각각 선택할 수 있으며, 최대 4표를 한 번에 제출합니다.
- 모든 참가자가 투표하면 가장 많은 표를 받은 가게가 확정됩니다.
- 가게와 `재추천`이 공동 1등이면 가게를 우선 확정합니다.
- `재추천`이 단독 1등이면 이전 후보를 제외한 새로운 가게 3곳으로 다음 투표를 생성합니다.

## 폴더 구조

```text
src/
├─ api/                    # Axios 공통 설정과 실제 HTTP 요청
├─ assets/                 # 카카오 폰트와 화면 아이콘
├─ components/
│  ├─ auth/                # 로그인 여부를 검사하는 보호 라우트
│  ├─ chat/                # 헤더, 공지, 메시지, 입력창
│  ├─ layout/              # 공통 모바일 상태바와 메인 화면 골격
│  └─ momeokji/            # 달력, 시간, 장소, 참가자, 투표 컴포넌트
├─ constants/              # 시간·테마·선택 옵션
├─ contexts/               # 로그인 사용자와 JWT 상태 Provider
├─ hooks/                  # 인증 Context 접근 훅
├─ pages/
│  ├─ ChatListPage.jsx     # 로그인 후 채팅방 목록과 검색
│  ├─ FriendListPage.jsx   # 사용자 프로필과 친구 검색
│  ├─ ChatRoomPage.jsx     # 채팅 및 투표 상태 조립
│  ├─ SettingsPage.jsx     # 계정·알림·로그아웃 설정
│  ├─ LoginPage.jsx        # 카카오·개발용 로그인 화면
│  └─ MomeokjiPage.jsx     # 8단계 설정 화면
├─ services/               # 화면값 변환과 응답 데이터 가공
├─ styles/                 # 공통 버튼·폰트 스타일
└─ utils/                  # 투표 집계 규칙
```

화면 CSS는 컴포넌트별 파일로 분리되어 있으며, 기능 단위 주석을 사용합니다.

## 환경 변수

프로젝트 루트에 `.env.local`을 만들고 필요한 값만 입력합니다.

```env
VITE_USE_MOCK=true
VITE_API_BASE_URL=
VITE_KAKAO_MAP_APP_KEY=
VITE_KAKAO_LOGIN_ENABLED=false
VITE_KAKAO_REST_API_KEY=
VITE_KAKAO_LOGIN_REDIRECT_URI=http://localhost:5173/oauth/kakao/callback
VITE_MOMEOKJI_AI_URL=
VITE_MOMEOKJI_RECOMMEND_URL=
VITE_PLACE_IMAGE_LOOKUP_URL=
```

- `VITE_USE_MOCK`: `true`면 프론트 목업, `false`면 실제 Spring API 사용
- `VITE_API_BASE_URL`: 배포 환경의 API 기본 주소. 개발 중에는 비워두면 `/api` 프록시 사용
- `VITE_KAKAO_MAP_APP_KEY`: 카카오 지도 JavaScript 키
- `VITE_KAKAO_LOGIN_ENABLED`: 카카오 OAuth 준비가 끝났을 때만 `true`로 변경
- `VITE_KAKAO_REST_API_KEY`: 카카오 OAuth 인가 화면에 사용하는 REST API 키
- `VITE_KAKAO_LOGIN_REDIRECT_URI`: 카카오 개발자 콘솔에 등록한 로그인 콜백 주소
- `VITE_MOMEOKJI_AI_URL`: 채팅에서 메뉴를 분석하는 백엔드 주소
- `VITE_MOMEOKJI_RECOMMEND_URL`: 조건에 맞는 가게 3곳을 추천하는 백엔드 주소
- `VITE_PLACE_IMAGE_LOOKUP_URL`: 추천 가게의 이미지 URL과 위치 정보를 조회하는 백엔드 주소

모임 API는 기본적으로 실제 백엔드를 호출합니다. 백엔드 없이 화면만 확인할 때는 로컬 `.env.local`에 `VITE_USE_MOCK=true`를 설정합니다. 기존 개별 AI·이미지 API 주소가 비어 있는 구간은 목업 데이터로 동작합니다. 실제 API 키와 비밀키는 Git에 커밋하지 않습니다.

실제 API 모드에서는 로그인 응답의 `accessToken`을 공통 API 클라이언트에 저장해야 합니다.

```js
import { setAccessToken } from './src/api/axiosInstance'

setAccessToken(loginResponse.accessToken)
```

## 로그인 구조

- 강사님 `f/login` 브랜치의 `AuthContext + PrivateRoute + LoginPage` 구조를 현재 API에 맞게 적용했습니다.
- 실제 카카오 로그인은 `/api/auth/kakao/login`에서 인가 코드를 JWT로 교환합니다.
- 로컬 개발 로그인은 Spring의 `dev` 프로필에서 `/api/dev/auth/login`을 사용합니다.
- 카카오 개발자 설정 전에는 `VITE_KAKAO_LOGIN_ENABLED=false`로 두며 로그인 화면에는 테스트 로그인만 표시됩니다.
- 백엔드 없이 화면만 확인할 때는 `VITE_USE_MOCK=true`로 개발용 로그인을 사용할 수 있습니다.
- `/chat/**`는 보호 라우트이며 로그인하지 않은 사용자는 `/login`으로 이동합니다.
- JWT와 최소 사용자 정보는 새로고침 복구를 위해 브라우저에 저장되고, 401 응답 시 자동으로 제거됩니다.

## API 연결 기준

- 프론트엔드는 선택값을 코드와 데이터 형태로 백엔드에 전달합니다.
- AI 추천 결과는 항상 중복되지 않는 가게 3곳이어야 합니다.
- 가게 이미지는 백엔드 검색 API가 전달한 `imageUrl`을 사용합니다.
- 장소 데이터는 `placeId`, `name`, `address`, `latitude`, `longitude` 형태로 관리합니다.
- 채팅방 참가 인원 표시는 `room.members.length`와 연결됩니다.

현재 API가 연결되지 않은 구간은 목업 데이터로 테스트할 수 있도록 구성되어 있습니다.

## 개발 서버 연동

- React 개발 서버: `http://localhost:5173`
- Spring Boot 개발 서버: `http://localhost:8081`
- 프론트의 `/api` 요청은 Vite 프록시를 통해 Spring Boot로 전달됩니다.
- 화면 기본 경로는 `/chat/1`이며 `/` 접속 시 자동으로 이동합니다.
