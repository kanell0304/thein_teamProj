import axiosInstance from './axiosInstance'

// ===== 로그인 사용자를 제외한 회원 목록 조회 =====
export async function fetchMembers({ signal } = {}) {
  const response = await axiosInstance.get('/members', { signal })
  return response.data
}
