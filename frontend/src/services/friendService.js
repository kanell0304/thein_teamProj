import {
  deleteFriendRequest,
  fetchFriends,
  fetchReceivedFriendRequests,
  postAcceptFriendRequest,
  postFriendRequest,
} from '../api/friendApi'

const USE_MOCK_API = String(import.meta.env.VITE_USE_MOCK ?? 'false').toLowerCase() === 'true'

// ===== 백엔드 연결 전에도 친구 화면을 확인할 수 있는 목업 =====
const MOCK_FRIENDS = [
  { id: 'member-seojun', nickname: '서준', profileImageUrl: null },
  { id: 'member-gyeongjun', nickname: '경준', profileImageUrl: null },
]
const MOCK_RECEIVED_REQUESTS = []

// ===== 실행 환경에 따라 목업 또는 실제 친구 API를 사용 =====
export async function getFriends({ signal } = {}) {
  if (USE_MOCK_API) return MOCK_FRIENDS

  try {
    return await fetchFriends({ signal })
  } catch (error) {
    throw new Error(error.userMessage || '친구 목록을 불러오지 못했습니다.', { cause: error })
  }
}

export async function getReceivedFriendRequests({ signal } = {}) {
  if (USE_MOCK_API) return MOCK_RECEIVED_REQUESTS

  try {
    return await fetchReceivedFriendRequests({ signal })
  } catch (error) {
    throw new Error(error.userMessage || '받은 친구 요청을 불러오지 못했습니다.', { cause: error })
  }
}

// ===== UID(회원 ID)로 친구 요청 보내기 =====
export async function sendFriendRequest(targetUserId) {
  try {
    return await postFriendRequest(targetUserId)
  } catch (error) {
    throw new Error(error.userMessage || '친구 요청을 보내지 못했습니다.', { cause: error })
  }
}

export async function acceptFriendRequest(requestId) {
  try {
    return await postAcceptFriendRequest(requestId)
  } catch (error) {
    throw new Error(error.userMessage || '친구 요청을 수락하지 못했습니다.', { cause: error })
  }
}

export async function rejectFriendRequest(requestId) {
  try {
    return await deleteFriendRequest(requestId)
  } catch (error) {
    throw new Error(error.userMessage || '친구 요청을 거절하지 못했습니다.', { cause: error })
  }
}
