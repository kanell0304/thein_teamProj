import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import MainScreen from '../components/layout/MainScreen'
import { getChatRooms } from '../services/chatRoomService'
import './ChatListPage.css'

// ===== API 날짜를 채팅 목록의 간단한 시각으로 표시 =====
function formatUpdatedAt(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

function ChatListPage() {
  const navigate = useNavigate()
  const searchInputRef = useRef(null)
  const [searchText, setSearchText] = useState('')
  const [rooms, setRooms] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

  // ===== 채팅방 목록을 조회하고 실패 시 재시도 가능한 상태로 전환 =====
  const requestRooms = useCallback(async (signal) => {
    try {
      setRooms(await getChatRooms({ signal }))
    } catch (error) {
      if (signal?.aborted) return
      setErrorMessage(error.message)
    } finally {
      if (!signal?.aborted) setIsLoading(false)
    }
  }, [])

  // ===== 사용자가 오류 안내에서 채팅방 조회를 다시 요청 =====
  const retryRooms = () => {
    setIsLoading(true)
    setErrorMessage('')
    requestRooms()
  }

  useEffect(() => {
    const controller = new AbortController()
    getChatRooms({ signal: controller.signal })
      .then(setRooms)
      .catch((error) => {
        if (!controller.signal.aborted) setErrorMessage(error.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })
    return () => controller.abort()
  }, [])

  // ===== 방 이름과 최근 메시지를 기준으로 목록 검색 =====
  const visibleRooms = useMemo(() => {
    const keyword = searchText.trim().toLowerCase()
    if (!keyword) return rooms

    return rooms.filter((room) => (
      `${room.name} ${room.lastMessage}`.toLowerCase().includes(keyword)
    ))
  }, [rooms, searchText])

  return (
    <MainScreen
      title="채팅"
      headerActions={(
        <button
          className="main-screen-action"
          type="button"
          aria-label="채팅방 검색"
          onClick={() => searchInputRef.current?.focus()}
        >
          <span className="material-symbols-outlined" aria-hidden="true">search</span>
        </button>
      )}
    >
      {/* ===== 채팅방 이름과 최근 메시지 검색 ===== */}
      <label className="main-search chat-list-search">
        <span className="material-symbols-outlined" aria-hidden="true">search</span>
        <span className="main-visually-hidden">채팅방 검색</span>
        <input
          ref={searchInputRef}
          type="search"
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          placeholder="채팅방 또는 메시지 검색"
        />
      </label>

      {/* ===== 조회 상태에 따라 로딩·오류·채팅방 목록 표시 ===== */}
      {isLoading ? (
        <p className="main-empty">채팅방을 불러오는 중이에요.</p>
      ) : errorMessage ? (
        <div className="main-request-state" role="alert">
          <p>{errorMessage}</p>
          <button type="button" onClick={retryRooms}>다시 시도</button>
        </div>
      ) : visibleRooms.length > 0 ? (
        <ul className="chat-list-rooms">
          {visibleRooms.map((room) => (
            <li key={room.id}>
              <button
                className="chat-list-room"
                type="button"
                onClick={() => navigate(`/chat/${room.id}`)}
              >
                <span className="chat-list-avatar" aria-hidden="true">진</span>
                <span className="chat-list-summary">
                  <span className="chat-list-room-title">
                    <strong>{room.name}</strong>
                    <small>{room.memberCount}</small>
                  </span>
                  <span className="chat-list-preview">{room.lastMessage}</span>
                </span>
                <span className="chat-list-meta">
                  <time dateTime={room.lastMessageAt || undefined}>
                    {formatUpdatedAt(room.lastMessageAt)}
                  </time>
                  {room.unreadCount > 0 && (
                    <span className="chat-list-badge" aria-label={`읽지 않은 메시지 ${room.unreadCount}개`}>
                      {room.unreadCount}
                    </span>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="main-empty">검색 결과가 없어요.</p>
      )}
    </MainScreen>
  )
}

export default ChatListPage
