import './ChatMessage.css'

function ChatMessage({ message }) {
  const isMine = message.sender === 'me'

  if (isMine) {
    return (
      <div className="message message--mine">
        <span className="message-time">{message.time}</span>
        <div className="message-bubble message-bubble--mine">{message.text}</div>
      </div>
    )
  }

  return (
    <div className="message message--other">
      <div className="message-profile" aria-hidden="true" />
      <div>
        <span className="message-name">{message.name}</span>
        <div className="message-row">
          <div className="message-bubble message-bubble--other">{message.text}</div>
          <span className="message-time">{message.time}</span>
        </div>
      </div>
    </div>
  )
}

export default ChatMessage
