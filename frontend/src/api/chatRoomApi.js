import axiosInstance from './axiosInstance'

// ===== 로그인 사용자가 참여한 채팅방 목록 조회 =====
export async function fetchChatRooms({ signal } = {}) {
  const response = await axiosInstance.get('/chatrooms', { signal })
  return response.data
}

// ===== 참가자를 선택해 새 채팅방 생성 =====
export async function postChatRoom({ name, participantIds }, { signal } = {}) {
  const response = await axiosInstance.post('/chatrooms', { name, participantIds }, { signal })
  return response.data
}

// ===== 채팅방 헤더에 표시할 이름 등 단건 정보 조회 =====
export async function fetchChatRoom(chatRoomId, { signal } = {}) {
  const response = await axiosInstance.get(`/chatrooms/${chatRoomId}`, { signal })
  return response.data
}
