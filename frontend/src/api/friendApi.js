import axiosInstance from './axiosInstance'

// ===== 서로 수락이 완료된 친구 목록 조회 =====
export async function fetchFriends({ signal } = {}) {
  const response = await axiosInstance.get('/friends', { signal })
  return response.data
}

// ===== 아직 응답하지 않은, 내가 받은 친구 요청 목록 조회 =====
export async function fetchReceivedFriendRequests({ signal } = {}) {
  const response = await axiosInstance.get('/friends/requests/received', { signal })
  return response.data
}

// ===== 상대방의 UID(회원 ID)로 친구 요청 보내기 =====
export async function postFriendRequest(targetUserId, { signal } = {}) {
  const response = await axiosInstance.post('/friends/requests', { targetUserId }, { signal })
  return response.data
}

// ===== 받은 친구 요청 수락 =====
export async function postAcceptFriendRequest(requestId, { signal } = {}) {
  const response = await axiosInstance.post(`/friends/requests/${requestId}/accept`, null, { signal })
  return response.data
}

// ===== 받은 친구 요청 거절 =====
export async function deleteFriendRequest(requestId, { signal } = {}) {
  const response = await axiosInstance.delete(`/friends/requests/${requestId}`, { signal })
  return response.data
}
