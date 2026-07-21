import axiosInstance from './axiosInstance'

// ===== 모먹지 모임 생성 REST 요청 =====
export async function postMeetup(request, { signal } = {}) {
  const response = await axiosInstance.post('/meetups', request, { signal })
  return response.data
}

// ===== 재접속 시 모먹지 모임 상태 조회 REST 요청 =====
export async function fetchMeetup(meetupId, { signal } = {}) {
  const response = await axiosInstance.get(`/meetups/${encodeURIComponent(meetupId)}`, {
    signal,
  })
  return response.data
}
