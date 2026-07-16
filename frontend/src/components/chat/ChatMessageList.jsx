import { useEffect, useRef } from 'react'
import ChatMessage from './ChatMessage'
import './ChatMessageList.css'

// ===== 같은 발신자가 같은 시각에 연속으로 보낸 일반 메시지를 하나의 묶음으로 판정 =====
function isSameMessageGroup(current, adjacent) {
  if (!current || !adjacent || current.type || adjacent.type) return false
  if (current.sender !== adjacent.sender || current.time !== adjacent.time) return false
  return current.sender === 'me' || current.name === adjacent.name
}

// ===== 카카오톡형 날짜 구분선 문구 생성 =====
function formatChatDate(date = new Date()) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(date)
}

function ChatMessageList({ messages, voteSession, onOpenVote }) {
  const listRef = useRef(null)

  useEffect(() => {
    // 메시지가 추가되면 가장 최근 메시지가 보이도록 아래로 이동합니다.
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages])

  return (
    <main
      ref={listRef}
      className="chat-message-list"
    >
      <div className="chat-message-list__content">
        {/* ===== 대화 날짜가 바뀌는 위치에 같은 컴포넌트를 추가할 수 있는 날짜 구분선 ===== */}
        <div className="chat-date-divider" role="separator" aria-label={formatChatDate()}>
          <span>{formatChatDate()}</span>
        </div>

        {/* 메시지 데이터 개수만큼 ChatMessage 컴포넌트를 반복 */}
        {messages.map((message, index) => {
          const isFirstInGroup = !isSameMessageGroup(message, messages[index - 1])
          const isLastInGroup = !isSameMessageGroup(message, messages[index + 1])

          return (
            <ChatMessage
              key={message.id}
              message={message}
              voteSession={voteSession}
              onOpenVote={onOpenVote}
              isFirstInGroup={isFirstInGroup}
              isLastInGroup={isLastInGroup}
            />
          )
        })}
      </div>
    </main>
  )
}

export default ChatMessageList
