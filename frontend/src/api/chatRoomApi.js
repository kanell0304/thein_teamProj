import axiosInstance from './axiosInstance'

// ===== 로그인 사용자가 참여한 채팅방 목록 조회 =====
export async function fetchChatRooms({ signal } = {}) {
  const response = await axiosInstance.get('/chatrooms', { signal })
  return response.data
}
