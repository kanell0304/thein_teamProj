import { Client } from '@stomp/stompjs'
import { ensureDevSession } from './authApi'

const WS_URL = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8081/ws'

// ===== 채팅방의 모든 실시간 토픽을 구독하고 연결된 STOMP 클라이언트를 돌려줌 =====
// handlers: { onMessage, onInvitation, onProgress, onRecommendationProgress, onVoteUpdate, onFinalNotice }
export async function connectChatSocket(chatRoomId, handlers = {}) {
  const session = await ensureDevSession()

  return new Promise((resolve, reject) => {
    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${session.accessToken}` },
      reconnectDelay: 3000,
      onConnect: () => {
        const subscribe = (topic, handler) => {
          if (!handler) return
          client.subscribe(`/topic/chatrooms/${chatRoomId}${topic}`, (frame) => {
            handler(JSON.parse(frame.body))
          })
        }

        subscribe('', handlers.onMessage)
        subscribe('/meetup-invitations', handlers.onInvitation)
        subscribe('/meetup-progress', handlers.onProgress)
        subscribe('/recommendation-progress', handlers.onRecommendationProgress)
        subscribe('/vote-updates', handlers.onVoteUpdate)
        subscribe('/final-notice', handlers.onFinalNotice)
        resolve(client)
      },
      onStompError: (frame) => reject(new Error(frame.headers?.message ?? 'STOMP 연결에 실패했습니다.')),
      onWebSocketError: () => reject(new Error('웹소켓 연결에 실패했습니다.')),
    })
    client.activate()
  })
}

export function sendChatMessage(client, chatRoomId, content) {
  if (!client?.connected) return
  client.publish({
    destination: `/app/chatrooms/${chatRoomId}/messages`,
    body: JSON.stringify({ content }),
  })
}

export function disconnectChatSocket(client) {
  client?.deactivate()
}
