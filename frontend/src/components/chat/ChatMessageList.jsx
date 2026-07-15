import { useEffect, useRef } from 'react'
import ChatMessage from './ChatMessage'
import './ChatMessageList.css'

function ChatMessageList({ messages }) {
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
        {/* 메시지 데이터 개수만큼 ChatMessage 컴포넌트를 반복 */}
        {messages.map((message) => (
          <ChatMessage key={message.id} message={message} />
        ))}
      </div>
    </main>
  )
}

export default ChatMessageList
