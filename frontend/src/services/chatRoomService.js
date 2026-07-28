import {
  deleteMyChatRoomMembership,
  fetchChatRooms,
  postChatRoom,
  postJoinChatRoomByCode,
} from '../api/chatRoomApi'

// ===== 실제 채팅방 API를 사용 =====
export async function getChatRooms({ signal } = {}) {
  try {
    return await fetchChatRooms({ signal })
  } catch (error) {
    throw new Error(error.userMessage || '채팅방 목록을 불러오지 못했습니다.', { cause: error })
  }
}

// ===== 선택한 친구들과 함께 새 채팅방을 만들고 바로 입장할 수 있게 방 정보를 돌려줌 =====
export async function createNewChatRoom({ name, participantIds }) {
  try {
    return await postChatRoom({ name, participantIds })
  } catch (error) {
    throw new Error(error.userMessage || '채팅방을 만들지 못했습니다.', { cause: error })
  }
}

// ===== 공유 코드를 입력해 채팅방에 중간 합류 =====
export async function joinChatRoomByCode(code) {
  try {
    return await postJoinChatRoomByCode(code)
  } catch (error) {
    throw new Error(error.userMessage || '코드로 채팅방에 참여하지 못했습니다.', { cause: error })
  }
}

// ===== 현재 사용자에게서만 채팅방을 제거하고 다른 참가자의 방은 유지 =====
export async function leaveChatRoom(chatRoomId) {
  try {
    await deleteMyChatRoomMembership(chatRoomId)
  } catch (error) {
    throw new Error(error.userMessage || '채팅방에서 나가지 못했습니다.', { cause: error })
  }
}
