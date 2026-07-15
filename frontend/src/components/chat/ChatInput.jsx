import { useState } from 'react'
import momeokjiIcon from '../../assets/icons/momeokji-icon.png'
import './ChatInput.css'

function ChatInput({ onSend }) {
  const [text, setText] = useState('')

  const handleSubmit = (event) => {
    event.preventDefault()
    const value = text.trim()
    if (!value) return
    // 부모가 전달한 함수만 호출하므로, 이후 REST/WebSocket 전송 함수로 교체
    onSend(value)
    setText('')
  }

  return (
    <form className="chat-input-form" onSubmit={handleSubmit}>
      <input
        type="text"
        value={text}
        aria-label="메시지"
        placeholder="메시지를 입력하세요"
        onChange={(event) => setText(event.target.value)}
      />
      <button className="momeokji-button" type="button" aria-label="모먹지 기능 열기">
        <span
          className="momeokji-glyph"
          style={{ '--momeokji-icon': `url("${momeokjiIcon}")` }}
          aria-hidden="true"
        />
      </button>
      <button className="emoji-button" type="button" aria-label="이모티콘">
        <span className="emoji-face" aria-hidden="true" />
      </button>
      <button className="sharp-button" type="submit" aria-label="메시지 전송">#</button>
    </form>
  )
}

export default ChatInput
