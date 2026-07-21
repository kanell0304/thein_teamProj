/**
 * 실제 로그인 화면이 붙기 전까지, 백엔드의 dev-login(dev 프로필 전용)으로 세션을 대신 발급받는 어댑터.
 * 나중에 카카오 로그인으로 교체할 때는 ensureDevSession 안의 발급 로직만 바꾸면 된다.
 */

const IDENTITY_STORAGE_KEY = 'momeogji_dev_kakao_id'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'

let sessionPromise = null

// ===== ?dev=이름 쿼리로 탭마다 다른 사용자를 흉내낼 수 있게, 없으면 브라우저에 고정 저장 =====
function resolveDevIdentity() {
  const queryDev = new URLSearchParams(window.location.search).get('dev')
  if (queryDev) {
    return { kakaoId: `dev-${queryDev}`, nickname: queryDev }
  }

  let kakaoId = window.localStorage.getItem(IDENTITY_STORAGE_KEY)
  if (!kakaoId) {
    kakaoId = `dev-${crypto.randomUUID().slice(0, 8)}`
    window.localStorage.setItem(IDENTITY_STORAGE_KEY, kakaoId)
  }
  return { kakaoId, nickname: '테스터' }
}

// ===== 세션은 탭당 한 번만 발급받아 재사용 =====
export function ensureDevSession() {
  if (!sessionPromise) {
    const { kakaoId, nickname } = resolveDevIdentity()
    sessionPromise = fetch(`${API_BASE_URL}/api/dev/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ kakaoId, nickname }),
    }).then((response) => {
      if (!response.ok) throw new Error('개발용 로그인에 실패했습니다.')
      return response.json()
    }).catch((error) => {
      sessionPromise = null
      throw error
    })
  }
  return sessionPromise
}
