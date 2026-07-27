import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import MainScreen from '../components/layout/MainScreen'
import { joinChatRoomByCode } from '../services/chatRoomService'
import './NewChatRoomPage.css'

function ChatRoomJoinPage() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [isJoining, setIsJoining] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const handleJoin = async () => {
    const trimmedCode = code.trim()
    if (!trimmedCode || isJoining) return

    setIsJoining(true)
    setErrorMessage('')
    try {
      const room = await joinChatRoomByCode(trimmedCode)
      navigate(`/chat/${room.id}`, { replace: true })
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsJoining(false)
    }
  }

  return (
    <MainScreen
      title="코드로 참여"
      headerActions={(
        <button
          className="new-chat-submit"
          type="button"
          disabled={!code.trim() || isJoining}
          onClick={handleJoin}
        >
          {isJoining ? '참여하는 중...' : '참여하기'}
        </button>
      )}
    >
      <label className="new-chat-name-field">
        <span>참여 코드</span>
        <input
          type="text"
          value={code}
          onChange={(event) => setCode(event.target.value.toUpperCase())}
          placeholder="채팅방 참여자에게 받은 코드를 입력해주세요"
          autoCapitalize="characters"
        />
      </label>

      {errorMessage && (
        <p className="new-chat-error" role="alert">{errorMessage}</p>
      )}
    </MainScreen>
  )
}

export default ChatRoomJoinPage
