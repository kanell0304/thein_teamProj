import {
  deleteFriendRequest,
  fetchFriends,
  fetchReceivedFriendRequests,
  postAcceptFriendRequest,
  postFriendRequest,
} from '../api/friendApi'

// ===== 실제 친구 API를 사용 =====
export async function getFriends({ signal } = {}) {
  try {
    return await fetchFriends({ signal })
  } catch (error) {
    throw new Error(error.userMessage || '친구 목록을 불러오지 못했습니다.', { cause: error })
  }
}

export async function getReceivedFriendRequests({ signal } = {}) {
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
