import { apiFetch } from './apiClient'

export function getRecentMessages(chatRoomId) {
  return apiFetch(`/api/chatrooms/${chatRoomId}/messages`)
}

export function getChatRoomMenuKeywords(chatRoomId, featureStartedAt, participantIds) {
  return apiFetch(`/api/chatrooms/${chatRoomId}/menu-keywords`, {
    method: 'POST',
    body: JSON.stringify({ featureStartedAt, participantIds }),
  })
}

export function getChatRoomMembers(chatRoomId) {
  return apiFetch(`/api/chatrooms/${chatRoomId}/members`)
}
