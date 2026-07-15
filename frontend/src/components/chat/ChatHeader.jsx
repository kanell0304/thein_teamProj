import { useEffect, useState } from 'react'
import './ChatHeader.css'

function getCurrentTime() {
  const now = new Date()
  return `${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}`
}

function ChatHeader({ roomName, memberCount = 1, unreadCount = 1 }) {
  const [statusTime, setStatusTime] = useState(getCurrentTime)

  useEffect(() => {
    // 화면이 열려 있는 동안 상단 상태바 시각을 현재 시각으로 갱신합니다.
    const timerId = window.setInterval(() => {
      setStatusTime(getCurrentTime())
    }, 1000)

    return () => window.clearInterval(timerId)
  }, [])

  // roomName은 이후 채팅방 조회 API의 방 제목 값으로 교체할 수 있습니다.
  return (
    <header className="chat-header">
      <div className="status-bar">
        <time>{statusTime}</time>
        <div className="status-icons" aria-label="통신 상태">
          <span className="material-symbols-outlined" aria-hidden="true">
            signal_cellular_alt
          </span>
          <span className="material-symbols-outlined" aria-hidden="true">wifi</span>
          <span className="material-symbols-outlined" aria-hidden="true">battery_full</span>
        </div>
      </div>

      <div className="top-bar">
        <button className="back-button" type="button" aria-label="뒤로가기">
          <span aria-hidden="true">‹</span>
          <span className="unread-count">{unreadCount}</span>
        </button>
        <div className="room-info">
          <div className="room-info__upper">
            <h1>{roomName}</h1>
            <span className="member-count">{memberCount}</span>
          </div>
          <button className="share-button" type="button">
            공유하기 <span aria-hidden="true">⌄</span>
          </button>
        </div>
        <div className="header-buttons">
          <button type="button" aria-label="검색">
            <span className="material-symbols-outlined" aria-hidden="true">search</span>
          </button>
          <button type="button" aria-label="채팅방 메뉴">
            <span className="material-symbols-outlined" aria-hidden="true">menu</span>
          </button>
        </div>
      </div>
    </header>
  )
}

export default ChatHeader
