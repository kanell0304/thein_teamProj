import { useState } from 'react'
import ChatHeader from '../components/chat/ChatHeader'
import ChatNotice from '../components/chat/ChatNotice'
import ChatMessageList from '../components/chat/ChatMessageList'
import ChatInput from '../components/chat/ChatInput'
import './ChatRoomPage.css'

function currentTime() {
  return new Date().toLocaleTimeString('ko-KR', {
    hour: 'numeric',
    minute: '2-digit',
  })
}

// 채팅방을 처음 열었을 때의 시각으로 기본 대화를 생성.
function createInitialMessages() {
  const startedAt = currentTime()

  return [
    { id: 1, sender: 'me', text: '오늘 모먹지??', time: startedAt },
    {
      id: 2,
      sender: 'other',
      name: '서준',
      text: '저는 상관없어요 아무거나?',
      time: startedAt,
    },
    {
      id: 3,
      sender: 'me',
      text: '또 아무거나야? 좀골라봐바',
      time: startedAt,
    },
    { id: 4, 
    sender: 'other',
     name: '경준', 
     text: '모 먹지 써볼까요그럼?', 
     time: startedAt },
  ]
}

function ChatRoomPage() {
  // API 연결 후에는 createInitialMessages 대신 채팅 조회 응답으로 초기화
  const [messages, setMessages] = useState(createInitialMessages)

  const sendMessage = (text) => {
    setMessages((previous) => [
      ...previous,
      {
        id: crypto.randomUUID(),
        sender: 'me',
        text,
        time: currentTime(),
      },
    ])
  }

  return (
    <div className="chat-room">
      {/* 필요 없는 영역은 이 조립부에서 컴포넌트 한 줄만 제거. */}
      <ChatHeader roomName="서준버스 가즈아" />

      <div className="chat-body">
        {/* 실제 결과가 생기면 ChatNotice의 children으로 결과 컴포넌트를 전달. */}
        <ChatNotice text="모먹지 최종 결정" />
        <ChatMessageList messages={messages} />
      </div>

      {/* 이후 기능 버튼이나 모먹지 패널을 붙일 기준이 되는 하단 영역. */}
      <footer className="chat-input-area">
        <div className="chat-input-row">
          <button className="plus-button" type="button" aria-label="채팅 기능 열기">
            <span className="plus-icon" aria-hidden="true" />
          </button>
          <ChatInput onSend={sendMessage} />
        </div>
        <div className="home-bar" />
      </footer>
    </div>
  )
}

export default ChatRoomPage
