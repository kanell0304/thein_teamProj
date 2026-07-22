import axiosInstance from '../api/axiosInstance'

// ===== 인증 토큰 부착 + 에러 메시지 통일이 필요한 REST 호출 공통 래퍼 =====
export async function apiFetch(path, options = {}) {
  const url = path.replace(/^\/api(?=\/|$)/, '') || '/'
  let data = options.body

  if (typeof data === 'string') {
    try {
      data = JSON.parse(data)
    } catch {
      // JSON이 아닌 본문은 문자열 그대로 전달합니다.
    }
  }

  const response = await axiosInstance.request({
    url,
    method: options.method ?? 'GET',
    headers: options.headers,
    data,
    signal: options.signal,
  })
  return response.data ?? null
}
