import { fetchChatRooms } from '../api/chatRoomApi'
import { createChatRoom, seedDevChat } from './chatApi'

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
    let rooms = await fetchChatRooms({ signal })

    // 개발 환경에서 참여 중인 방이 없으면 실제 DB 방과 예시 대화를 한 번만 준비합니다.
    if (import.meta.env.DEV && rooms.length === 0) {
      const room = await createChatRoom('진원버스 가즈아')
      await seedDevChat(room.id)
      rooms = await fetchChatRooms({ signal })
    }

    return rooms
  } catch (error) {
    throw new Error(error.userMessage || '채팅방 목록을 불러오지 못했습니다.', { cause: error })
  }
}
