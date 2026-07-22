import { apiFetch } from './apiClient'

export function createMeetup({ chatRoomId, commonOption, voteDeadlineAt = null, voteDurationMinutes = 10, participantIds }) {
  return apiFetch('/api/meetups', {
    method: 'POST',
    body: JSON.stringify({ chatRoomId, commonOption, voteDeadlineAt, voteDurationMinutes, participantIds }),
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

// ===== 복수 선택 전체를 한 번에 교체해 마지막 참가자도 모든 선택을 안전하게 저장 =====
export function replaceVotes(meetupId, roundId, candidateIds) {
  return apiFetch(`/api/meetups/${meetupId}/rounds/${roundId}/votes`, {
    method: 'PUT',
    body: JSON.stringify({ candidateIds }),
  })
}

export function updateFinalNotice(meetupId, meetingDatetime) {
  return apiFetch(`/api/meetups/${meetupId}/final-notice`, {
    method: 'PATCH',
    body: JSON.stringify({ meetingDatetime }),
  })
}
