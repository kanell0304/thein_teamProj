import axios from 'axios'

export const ACCESS_TOKEN_STORAGE_KEY = 'momeokji.accessToken'
export const AUTH_UNAUTHORIZED_EVENT = 'momeokji:unauthorized'

// ===== 개발 환경에서는 Vite 프록시를 사용하고, 배포 환경에서는 지정 주소를 사용 =====
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')

const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ===== 로그인 성공 후 받은 JWT를 브라우저 저장소에서 관리 =====
export function getAccessToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY) || ''
}

export function setAccessToken(accessToken) {
  if (!accessToken) {
    window.localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
    return
  }
  window.localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, accessToken)
}

export function clearAccessToken() {
  window.localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
}

// ===== 모든 인증 API 요청에 저장된 JWT를 자동으로 추가 =====
axiosInstance.interceptors.request.use((config) => {
  if (config.skipAuth) return config

  const accessToken = getAccessToken()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

// ===== 화면에서 공통으로 사용할 수 있도록 Axios 오류 메시지를 정규화 =====
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status ?? 0
    const responseData = error.response?.data
    const message = responseData?.error
      || responseData?.message
      || (status === 401 || status === 403
        ? '로그인이 필요하거나 접근 권한이 없습니다.'
        : status
          ? `요청 처리에 실패했습니다. (${status})`
          : '백엔드 서버에 연결할 수 없습니다.')

    // 인증 만료를 AuthContext에도 알려 보호 라우트 상태를 함께 정리합니다.
    if (status === 401) {
      clearAccessToken()
      window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT))
    }

    error.userMessage = message
    return Promise.reject(error)
  },
)

export default axiosInstance
