import { apiFetch } from './apiClient'

export function createMeetup({ chatRoomId, commonOption, voteDeadlineAt = null, participantIds }) {
  return apiFetch('/api/meetups', {
    method: 'POST',
    body: JSON.stringify({ chatRoomId, commonOption, voteDeadlineAt, participantIds }),
  })
}

export function getMeetupDetail(meetupId) {
  return apiFetch(`/api/meetups/${meetupId}`)
}

// ===== 재접속/새로고침 시 이 채팅방에서 가장 최근 모임을 복원합니다. 없으면 null. =====
export function getActiveMeetup(chatRoomId) {
  return apiFetch(`/api/meetups/active?chatRoomId=${chatRoomId}`)
}

export function getMeetupParticipants(meetupId) {
  return apiFetch(`/api/meetups/${meetupId}/participants`)
}

export function submitMyPreference(meetupId, preference) {
  return apiFetch(`/api/meetups/${meetupId}/preferences/me`, {
    method: 'POST',
    body: JSON.stringify(preference),
  })
}

export function forceStartRecommendation(meetupId) {
  return apiFetch(`/api/meetups/${meetupId}/preferences/force-start`, { method: 'POST' })
}

export function castVote(meetupId, roundId, roundCandidateId) {
  return apiFetch(`/api/meetups/${meetupId}/rounds/${roundId}/candidates/${roundCandidateId}/votes`, {
    method: 'POST',
  })
}

export function retractVote(meetupId, roundId, roundCandidateId) {
  return apiFetch(`/api/meetups/${meetupId}/rounds/${roundId}/candidates/${roundCandidateId}/votes`, {
    method: 'DELETE',
  })
}

export function updateFinalNotice(meetupId, meetingDatetime) {
  return apiFetch(`/api/meetups/${meetupId}/final-notice`, {
    method: 'PATCH',
    body: JSON.stringify({ meetingDatetime }),
  })
}
