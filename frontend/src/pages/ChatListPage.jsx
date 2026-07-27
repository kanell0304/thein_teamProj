import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import MainScreen from '../components/layout/MainScreen'
import { ChatAddIcon, ChatJoinIcon } from '../components/layout/HeaderActionIcons'
import { getChatRooms, leaveChatRoom } from '../services/chatRoomService'
import './ChatListPage.css'

// ===== 카카오톡 스타일을 참고한 채팅방 나가기 아이콘 =====
function ChatLeaveIcon() {
  return (
    <svg
      className="chat-list-leave-icon"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path d="M10 5H6.8A1.8 1.8 0 0 0 5 6.8v10.4A1.8 1.8 0 0 0 6.8 19H10" />
      <path d="M13.5 8.5 17 12l-3.5 3.5M8.5 12H17" />
    </svg>
  )
}

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
  const [leaveErrorMessage, setLeaveErrorMessage] = useState('')
  const [leavingRoomId, setLeavingRoomId] = useState(null)
  const [pendingLeaveRoom, setPendingLeaveRoom] = useState(null)
  const [leaveToastMessage, setLeaveToastMessage] = useState('')

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

  // ===== 확인 모달에서 승인하면 현재 사용자만 채팅방에서 나가기 =====
  const confirmLeaveRoom = async () => {
    if (!pendingLeaveRoom) return

    const room = pendingLeaveRoom
    setLeaveErrorMessage('')
    setLeavingRoomId(room.id)
    try {
      await leaveChatRoom(room.id)
      setRooms((previous) => previous.filter((item) => item.id !== room.id))
      setPendingLeaveRoom(null)
      setLeaveToastMessage(`'${room.name}' 채팅방에서 나갔어요.`)
    } catch (error) {
      setLeaveErrorMessage(error.message)
      setPendingLeaveRoom(null)
    } finally {
      setLeavingRoomId(null)
    }
  }

  // ===== 완료 토스트를 잠시 보여준 뒤 자동으로 닫기 =====
  useEffect(() => {
    if (!leaveToastMessage) return undefined
    const timeoutId = window.setTimeout(() => setLeaveToastMessage(''), 2500)
    return () => window.clearTimeout(timeoutId)
  }, [leaveToastMessage])

  // ===== 확인 모달은 ESC 키로 취소 가능 =====
  useEffect(() => {
    if (!pendingLeaveRoom) return undefined
    const closeWithEscape = (event) => {
      if (event.key === 'Escape' && leavingRoomId == null) setPendingLeaveRoom(null)
    }
    window.addEventListener('keydown', closeWithEscape)
    return () => window.removeEventListener('keydown', closeWithEscape)
  }, [leavingRoomId, pendingLeaveRoom])

  return (
    <MainScreen
      title="채팅"
      headerActions={(
        <>
          <button
            className="main-screen-action"
            type="button"
            aria-label="채팅방 검색"
            onClick={() => searchInputRef.current?.focus()}
          >
            <span className="material-symbols-outlined" aria-hidden="true">search</span>
          </button>
          <button
            className="main-screen-action"
            type="button"
            aria-label="코드로 채팅방 참여"
            onClick={() => navigate('/chats/join')}
          >
            <ChatJoinIcon />
          </button>
          <button
            className="main-screen-action"
            type="button"
            aria-label="새 채팅방 만들기"
            onClick={() => navigate('/chats/new')}
          >
            <ChatAddIcon />
          </button>
        </>
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

      {leaveErrorMessage && (
        <p className="chat-list-action-error" role="alert">{leaveErrorMessage}</p>
      )}

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
            <li className="chat-list-item" key={room.id}>
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
              <button
                className="chat-list-leave"
                type="button"
                aria-label={`${room.name} 채팅방 나가기`}
                disabled={leavingRoomId === room.id}
                onClick={() => setPendingLeaveRoom(room)}
              >
                <ChatLeaveIcon />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="main-empty">검색 결과가 없어요.</p>
      )}

      {/* ===== 채팅방 나가기 확인 모달 ===== */}
      {pendingLeaveRoom && (
        <div
          className="chat-list-dialog-backdrop"
          role="presentation"
          onPointerDown={(event) => {
            if (event.target === event.currentTarget && leavingRoomId == null) {
              setPendingLeaveRoom(null)
            }
          }}
        >
          <section
            className="chat-list-dialog"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="chat-list-leave-title"
            aria-describedby="chat-list-leave-description"
          >
            <h2 id="chat-list-leave-title">채팅방에서 나갈까요?</h2>
            <p id="chat-list-leave-description">
              <strong>{pendingLeaveRoom.name}</strong>의 대화 목록에서 나가요.
              다시 참여하려면 초대 코드가 필요할 수 있어요.
            </p>
            <div className="chat-list-dialog-actions">
              <button
                type="button"
                disabled={leavingRoomId != null}
                onClick={() => setPendingLeaveRoom(null)}
              >
                취소
              </button>
              <button
                className="chat-list-dialog-leave"
                type="button"
                disabled={leavingRoomId != null}
                onClick={confirmLeaveRoom}
              >
                {leavingRoomId != null ? '나가는 중…' : '나가기'}
              </button>
            </div>
          </section>
        </div>
      )}

      {/* ===== 채팅방 나가기 완료 토스트 ===== */}
      {leaveToastMessage && (
        <p className="chat-list-toast" role="status" aria-live="polite">
          {leaveToastMessage}
        </p>
      )}
    </MainScreen>
  )
}

export default ChatListPage
