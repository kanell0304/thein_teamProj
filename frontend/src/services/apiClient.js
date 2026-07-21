import { API_BASE_URL, ensureDevSession } from './authApi'

// ===== 인증 토큰 부착 + 에러 메시지 통일이 필요한 REST 호출 공통 래퍼 =====
export async function apiFetch(path, options = {}) {
  const session = await ensureDevSession()
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.accessToken}`,
      ...options.headers,
    },
  })

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    throw new Error(errorBody?.error ?? `요청에 실패했습니다. (${response.status})`)
  }

  // 일부 엔드포인트(방 참여 등)는 본문 없이 200을 반환한다.
  const text = await response.text()
  return text ? JSON.parse(text) : null
}
