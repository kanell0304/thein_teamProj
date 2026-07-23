import { apiFetch } from './apiClient'

const TEST_ROOM_STORAGE_KEY = 'momeogji_dev_chat_room_id'

export function createChatRoom(name) {
  return apiFetch('/api/chatrooms', { method: 'POST', body: JSON.stringify({ name }) })
}

export function joinChatRoom(chatRoomId) {
  return apiFetch(`/api/chatrooms/${chatRoomId}/join`, { method: 'POST' })
}

export function getRecentMessages(chatRoomId) {
  return apiFetch(`/api/chatrooms/${chatRoomId}/messages`)
}

export function getChatRoomMenuKeywords(chatRoomId, featureStartedAt) {
  const query = new URLSearchParams({ featureStartedAt }).toString()
  return apiFetch(`/api/chatrooms/${chatRoomId}/menu-keywords?${query}`)
}

export function getChatRoomMembers(chatRoomId) {
  return apiFetch(`/api/chatrooms/${chatRoomId}/members`)
}

// ===== dev 프로필의 빈 방에 실제 저장·웹소켓 경로를 사용하는 예시 대화 생성 =====
export function seedDevChat(chatRoomId) {
  return apiFetch(`/api/dev/chatrooms/${chatRoomId}/seed`, { method: 'POST' })
}

// ===== 로그인/방 목록 화면이 없는 동안 테스트용 채팅방 하나를 자동으로 준비 =====
export async function ensureTestChatRoom() {
  // 이 브라우저에서 이미 join/생성에 성공한 방이 있으면 그걸 우선한다. 백엔드 재시작으로 DB가
  // 초기화된 직후 여러 탭이 동시에 열리면 env 값만 믿고 각자 새 방을 만들어버리는 레이스가 생기기 때문에,
  // 한 번 자리 잡은 방을 최대한 재사용해 흔들림을 줄인다.
  const candidateId = window.localStorage.getItem(TEST_ROOM_STORAGE_KEY) || import.meta.env.VITE_DEV_CHAT_ROOM_ID

  if (candidateId) {
    try {
      await joinChatRoom(candidateId)
      window.localStorage.setItem(TEST_ROOM_STORAGE_KEY, String(candidateId))
      return Number(candidateId)
    } catch {
      // 방이 없으면 아래에서 새로 만든다.
    }
  }

  const room = await createChatRoom('모먹지 테스트방')
  window.localStorage.setItem(TEST_ROOM_STORAGE_KEY, String(room.id))
  console.info(
    `[momeokji] 테스트 채팅방을 새로 만들었어요 (id=${room.id}). 다른 탭이나 팀원과 같은 방에서 테스트하려면 `
    + `frontend/.env의 VITE_DEV_CHAT_ROOM_ID=${room.id} 로 맞춰주세요.`,
  )
  return room.id
}
