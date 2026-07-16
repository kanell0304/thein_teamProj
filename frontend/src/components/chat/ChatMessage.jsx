import './ChatMessage.css'
import MomeokjiVoteBubble from '../momeokji/MomeokjiVoteBubble'

function ChatMessage({
  message,
  voteSession,
  onOpenVote,
  isFirstInGroup = true,
  isLastInGroup = true,
}) {
  const isMine = message.sender === 'me'
  const groupClassName = isFirstInGroup ? '' : ' message--continuation'
  const bubbleGroupClassName = isFirstInGroup ? '' : ' message-bubble--continuation'

  // ===== 일반 텍스트와 분리된 모먹지 투표 기능 메시지 =====
  if (message.type === 'MOMEOKJI_VOTE') {
    const isCurrentSession = voteSession?.id === message.voteSessionId

    return (
      <div className="message message--mine message--momeokji">
        <span className="message-time">{message.time}</span>
        <MomeokjiVoteBubble
          status={isCurrentSession ? voteSession.status : 'CLOSED'}
          onOpen={isCurrentSession ? onOpenVote : undefined}
        />
      </div>
    )
  }

  if (isMine) {
    return (
      <div className={`message message--mine${groupClassName}`}>
        {isLastInGroup && <span className="message-time">{message.time}</span>}
        <div className={`message-bubble message-bubble--mine${bubbleGroupClassName}`}>{message.text}</div>
      </div>
    )
  }

  return (
    <div className={`message message--other${groupClassName}`}>
      {isFirstInGroup
        ? <div className="message-profile" aria-hidden="true" />
        : <div className="message-profile-spacer" aria-hidden="true" />}
      <div className="message-content">
        {isFirstInGroup && <span className="message-name">{message.name}</span>}
        <div className="message-row">
          <div className={`message-bubble message-bubble--other${bubbleGroupClassName}`}>{message.text}</div>
          {isLastInGroup && <span className="message-time">{message.time}</span>}
        </div>
      </div>
    </div>
  )
}

export default ChatMessage
