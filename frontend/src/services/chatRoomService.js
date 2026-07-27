import {
  deleteMyChatRoomMembership,
  fetchChatRooms,
  postChatRoom,
  postJoinChatRoomByCode,
} from '../api/chatRoomApi'

const USE_MOCK_API = String(import.meta.env.VITE_USE_MOCK ?? 'false').toLowerCase() === 'true'

// ===== 백엔드 연결 전에도 채팅 목록 화면을 확인할 수 있는 목업 =====
const MOCK_CHAT_ROOMS = [
  {
    id: 1,
    name: '진원버스 가즈아',
    memberCount: 3,
    lastMessage: '모 먹지 써볼까요그럼?',
    lastMessageAt: new Date().toISOString(),
    unreadCount: 1,
  },
]

// ===== 실행 환경에 따라 목업 또는 실제 채팅방 API를 사용 =====
export async function getChatRooms({ signal } = {}) {
  if (USE_MOCK_API) return MOCK_CHAT_ROOMS

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
  if (USE_MOCK_API) return

  try {
    await deleteMyChatRoomMembership(chatRoomId)
  } catch (error) {
    throw new Error(error.userMessage || '채팅방에서 나가지 못했습니다.', { cause: error })
  }
}
