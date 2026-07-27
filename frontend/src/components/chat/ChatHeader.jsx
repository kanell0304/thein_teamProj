import { useState } from 'react'
import MobileStatusBar from '../layout/MobileStatusBar'
import './ChatHeader.css'

function ChatHeader({ roomName, memberCount = 1, unreadCount = 1, joinCode, onBack }) {
  // roomName은 이후 채팅방 조회 API의 방 제목 값으로 교체할 수 있습니다.
  const [isCodeVisible, setIsCodeVisible] = useState(false)
  const [copyStatus, setCopyStatus] = useState('')

  const handleShareClick = () => {
    setIsCodeVisible((visible) => !visible)
    setCopyStatus('')
  }

  const handleCopyCode = async () => {
    try {
      await navigator.clipboard.writeText(joinCode)
      setCopyStatus('복사됐어요')
    } catch {
      setCopyStatus('복사에 실패했어요')
    }
  }

  return (
    <header className="chat-header">
      <MobileStatusBar tone="bordered" />

      <div className="top-bar">
        <button className="back-button" type="button" aria-label="뒤로가기" onClick={onBack}>
          <span aria-hidden="true">‹</span>
          <span className="unread-count">{unreadCount}</span>
        </button>
        <div className="room-info">
          <div className="room-info__upper">
            <h1>{roomName}</h1>
            <span className="member-count">{memberCount}</span>
          </div>
          <button className="share-button" type="button" onClick={handleShareClick} disabled={!joinCode}>
            공유하기 <span aria-hidden="true">⌄</span>
          </button>
          {isCodeVisible && joinCode && (
            <div className="room-code-popover" role="dialog" aria-label="채팅방 참여 코드">
              <span className="room-code-popover__label">참여 코드</span>
              <strong className="room-code-popover__code">{joinCode}</strong>
              <button type="button" className="room-code-popover__copy" onClick={handleCopyCode}>
                복사
              </button>
              {copyStatus && <span className="room-code-popover__status">{copyStatus}</span>}
            </div>
          )}
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
