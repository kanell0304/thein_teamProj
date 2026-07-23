import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import MainScreen from '../components/layout/MainScreen'
import { getMembers } from '../services/memberService'
import { createNewChatRoom } from '../services/chatRoomService'
import './FriendListPage.css'
import './NewChatRoomPage.css'

function NewChatRoomPage() {
  const navigate = useNavigate()
  const [friends, setFriends] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadErrorMessage, setLoadErrorMessage] = useState('')
  const [selectedIds, setSelectedIds] = useState([])
  const [editedName, setEditedName] = useState(null)
  const [isCreating, setIsCreating] = useState(false)
  const [createErrorMessage, setCreateErrorMessage] = useState('')

  // ===== 친구 목록을 조회하고 실패 시 재시도 가능한 상태로 전환 =====
  const requestFriends = (signal) => {
    getMembers({ signal })
      .then(setFriends)
      .catch((error) => {
        if (!signal?.aborted) setLoadErrorMessage(error.message)
      })
      .finally(() => {
        if (!signal?.aborted) setIsLoading(false)
      })
  }

  useEffect(() => {
    const controller = new AbortController()
    requestFriends(controller.signal)
    return () => controller.abort()
  }, [])

  const retryFriends = () => {
    setIsLoading(true)
    setLoadErrorMessage('')
    requestFriends()
  }

  // ===== 선택된 친구 닉네임을 이어 붙인 기본 채팅방 이름 =====
  const defaultName = useMemo(() => (
    friends
      .filter((friend) => selectedIds.includes(friend.id))
      .map((friend) => friend.nickname)
      .join(', ')
  ), [friends, selectedIds])

  // ===== 사용자가 직접 고치기 전까지는 화면에 선택 친구 기반 기본 이름을 보여줌 =====
  const displayedName = editedName ?? defaultName

  const toggleFriend = (friendId) => {
    setSelectedIds((previous) => (
      previous.includes(friendId)
        ? previous.filter((id) => id !== friendId)
        : [...previous, friendId]
    ))
  }

  const handleCreate = async () => {
    if (selectedIds.length === 0 || isCreating) return

    setIsCreating(true)
    setCreateErrorMessage('')
    try {
      const room = await createNewChatRoom({
        name: displayedName.trim() || defaultName,
        participantIds: selectedIds,
      })
      navigate(`/chat/${room.id}`, { replace: true })
    } catch (error) {
      setCreateErrorMessage(error.message)
    } finally {
      setIsCreating(false)
    }
  }

  return (
    <MainScreen
      title="새 채팅방"
      headerActions={(
        <button
          className="new-chat-submit"
          type="button"
          disabled={selectedIds.length === 0 || isCreating}
          onClick={handleCreate}
        >
          {isCreating ? '만드는 중...' : '만들기'}
        </button>
      )}
    >
      <label className="new-chat-name-field">
        <span>채팅방 이름</span>
        <input
          type="text"
          value={displayedName}
          onChange={(event) => setEditedName(event.target.value)}
          placeholder="친구를 선택하면 자동으로 채워져요"
        />
      </label>

      {createErrorMessage && (
        <p className="new-chat-error" role="alert">{createErrorMessage}</p>
      )}

      <section className="friend-section friend-section--list" aria-labelledby="new-chat-friend-title">
        <h2 id="new-chat-friend-title">함께할 친구 {selectedIds.length > 0 ? selectedIds.length : ''}</h2>
        {isLoading ? (
          <p className="main-empty friend-empty">친구를 불러오는 중이에요.</p>
        ) : loadErrorMessage ? (
          <div className="main-request-state friend-empty" role="alert">
            <p>{loadErrorMessage}</p>
            <button type="button" onClick={retryFriends}>다시 시도</button>
          </div>
        ) : friends.length > 0 ? (
          <div className="friend-list">
            {friends.map((friend) => (
              <label className="friend-profile new-chat-friend-row" key={friend.id}>
                <input
                  type="checkbox"
                  checked={selectedIds.includes(friend.id)}
                  onChange={() => toggleFriend(friend.id)}
                />
                {friend.profileImageUrl ? (
                  <img className="friend-avatar friend-avatar--image" src={friend.profileImageUrl} alt="" />
                ) : (
                  <span className="friend-avatar" aria-hidden="true">{friend.nickname.slice(0, 1)}</span>
                )}
                <span className="friend-summary">
                  <strong>{friend.nickname}</strong>
                </span>
              </label>
            ))}
          </div>
        ) : (
          <p className="main-empty friend-empty">함께할 친구가 없어요.</p>
        )}
      </section>
    </MainScreen>
  )
}

export default NewChatRoomPage
