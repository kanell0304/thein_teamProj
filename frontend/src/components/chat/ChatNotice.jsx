import { useState } from 'react'
import momeokjiIcon from '../../assets/icons/momeokji-icon.png'
import './ChatNotice.css'

function ChatNotice({ text, children, defaultExpanded = false }) {
  const hasDetails = children != null
  const [isExpanded, setIsExpanded] = useState(defaultExpanded)

  return (
    <section className={`chat-notice${isExpanded ? ' chat-notice--expanded' : ''}`}>
      <button
        className="notice-summary"
        type="button"
        aria-expanded={hasDetails ? isExpanded : undefined}
        disabled={!hasDetails}
        onClick={() => hasDetails && setIsExpanded((previous) => !previous)}
      >
        <img className="notice-icon" src={momeokjiIcon} alt="" aria-hidden="true" />
        <span className="notice-title">{text}</span>
        <span className="material-symbols-outlined notice-arrow" aria-hidden="true">
          expand_more
        </span>
      </button>

      {/* 추천·투표·예약 등 다른 기능의 결과 컴포넌트를 children 위치에 넣습니다. */}
      {hasDetails && (
        <div className="notice-details" aria-hidden={!isExpanded}>
          <div>
            <div className="notice-details__content">{children}</div>
          </div>
        </div>
      )}
    </section>
  )
}

export default ChatNotice
