import axiosInstance from './axiosInstance'

// ===== 개발 환경에서 카카오 인증 없이 테스트 JWT를 발급 =====
export async function postDevLogin({ kakaoId, nickname }, { signal } = {}) {
  const response = await axiosInstance.post('/dev/auth/login', {
    kakaoId,
    nickname,
  }, {
    signal,
    skipAuth: true,
  })
  return response.data
}

// ===== 카카오가 전달한 인가 코드를 백엔드 로그인 API로 교환 =====
export async function postKakaoLogin(code, { signal } = {}) {
  const response = await axiosInstance.post('/auth/kakao/login', { code }, {
    signal,
    skipAuth: true,
  })
  return response.data
}
