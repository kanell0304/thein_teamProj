import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import MainScreen from '../components/layout/MainScreen'
import { FriendAddIcon } from '../components/layout/HeaderActionIcons'
import useAuth from '../hooks/useAuth'
import {
  acceptFriendRequest,
  getFriends,
  getReceivedFriendRequests,
  rejectFriendRequest,
  sendFriendRequest,
} from '../services/friendService'
import './FriendListPage.css'

function FriendProfile({ nickname, statusMessage, profileImageUrl, isMe = false, actions = null }) {
  return (
    <article className={`friend-profile${isMe ? ' friend-profile--me' : ''}`}>
      {profileImageUrl ? (
        <img className="friend-avatar friend-avatar--image" src={profileImageUrl} alt="" />
      ) : (
        <span className="friend-avatar" aria-hidden="true">{nickname.slice(0, 1)}</span>
      )}
      <span className="friend-summary">
        <strong>{nickname}</strong>
        {statusMessage && <small>{statusMessage}</small>}
      </span>
      {actions}
    </article>
  )
}

function FriendListPage() {
  const { user } = useAuth()
  const searchInputRef = useRef(null)
  const [searchText, setSearchText] = useState('')
  const [friends, setFriends] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')
  const [receivedRequests, setReceivedRequests] = useState([])
  const [isAddFormVisible, setIsAddFormVisible] = useState(false)
  const [targetUid, setTargetUid] = useState('')
  const [isSendingRequest, setIsSendingRequest] = useState(false)
  const [addRequestMessage, setAddRequestMessage] = useState('')
  const currentNickname = user?.nickname || user?.name || '사용자'

  // ===== 친구 목록과 받은 요청을 함께 조회하고 실패 시 재시도 가능한 상태로 전환 =====
  const requestFriends = useCallback(async (signal) => {
    try {
      const [friendList, requests] = await Promise.all([
        getFriends({ signal }),
        getReceivedFriendRequests({ signal }),
      ])
      setFriends(friendList)
      setReceivedRequests(requests)
    } catch (error) {
      if (signal?.aborted) return
      setErrorMessage(error.message)
    } finally {
      if (!signal?.aborted) setIsLoading(false)
    }
  }, [])

  const retryFriends = () => {
    setIsLoading(true)
    setErrorMessage('')
    requestFriends()
  }

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      getFriends({ signal: controller.signal }),
      getReceivedFriendRequests({ signal: controller.signal }),
    ])
      .then(([friendList, requests]) => {
        setFriends(friendList)
        setReceivedRequests(requests)
      })
      .catch((error) => {
        if (!controller.signal.aborted) setErrorMessage(error.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })
    return () => controller.abort()
  }, [])

  // ===== 닉네임을 기준으로 친구 검색 =====
  const visibleFriends = useMemo(() => {
    const keyword = searchText.trim().toLowerCase()
    if (!keyword) return friends
    return friends.filter((friend) => friend.nickname.toLowerCase().includes(keyword))
  }, [friends, searchText])

  const handleToggleAddForm = () => {
    setIsAddFormVisible((visible) => !visible)
    setAddRequestMessage('')
  }

  // ===== UID로 친구 요청 전송 =====
  const handleSendRequest = async () => {
    const trimmedUid = targetUid.trim()
    if (!trimmedUid || isSendingRequest) return

    setIsSendingRequest(true)
    setAddRequestMessage('')
    try {
      await sendFriendRequest(Number(trimmedUid))
      setTargetUid('')
      setAddRequestMessage('친구 요청을 보냈어요.')
    } catch (error) {
      setAddRequestMessage(error.message)
    } finally {
      setIsSendingRequest(false)
    }
  }

  // ===== 받은 요청 수락: 목록을 다시 불러와 친구/요청 상태를 함께 반영 =====
  const handleAccept = async (requestId) => {
    try {
      await acceptFriendRequest(requestId)
      requestFriends()
    } catch (error) {
      setErrorMessage(error.message)
    }
  }

  const handleReject = async (requestId) => {
    try {
      await rejectFriendRequest(requestId)
      setReceivedRequests((previous) => previous.filter((request) => request.requestId !== requestId))
    } catch (error) {
      setErrorMessage(error.message)
    }
  }

  return (
    <MainScreen
      title="친구"
      headerActions={(
        <>
          <button
            className="main-screen-action"
            type="button"
            aria-label="친구 검색"
            onClick={() => searchInputRef.current?.focus()}
          >
            <span className="material-symbols-outlined" aria-hidden="true">search</span>
          </button>
          <button
            className="main-screen-action"
            type="button"
            aria-label="친구 추가"
            onClick={handleToggleAddForm}
          >
            <FriendAddIcon />
          </button>
        </>
      )}
    >
      {/* ===== 사용자 닉네임 검색 ===== */}
      <label className="main-search">
        <span className="material-symbols-outlined" aria-hidden="true">search</span>
        <span className="main-visually-hidden">친구 검색</span>
        <input
          ref={searchInputRef}
          type="search"
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          placeholder="이름 검색"
        />
      </label>

      {/* ===== UID로 친구 요청 보내기 ===== */}
      {isAddFormVisible && (
        <section className="friend-add-form" aria-label="UID로 친구 추가">
          <label>
            <span>상대방 UID</span>
            <input
              type="number"
              value={targetUid}
              onChange={(event) => setTargetUid(event.target.value)}
              placeholder="설정 화면에서 확인한 UID를 입력해주세요"
            />
          </label>
          <button
            type="button"
            className="friend-add-form__submit"
            disabled={!targetUid.trim() || isSendingRequest}
            onClick={handleSendRequest}
          >
            {isSendingRequest ? '요청 보내는 중...' : '요청 보내기'}
          </button>
          {addRequestMessage && <p className="friend-add-form__message">{addRequestMessage}</p>}
        </section>
      )}

      {/* ===== 로그인한 사용자의 프로필 ===== */}
      <section className="friend-section" aria-labelledby="my-profile-title">
        <h2 id="my-profile-title">내 프로필</h2>
        <FriendProfile
          nickname={currentNickname}
          statusMessage="오늘 모 먹지?"
          profileImageUrl={user?.profileImageUrl}
          isMe
        />
      </section>

      {/* ===== 아직 수락/거절하지 않은 받은 친구 요청 ===== */}
      {receivedRequests.length > 0 && (
        <section className="friend-section friend-section--list" aria-labelledby="friend-request-title">
          <h2 id="friend-request-title">받은 친구 요청 {receivedRequests.length}</h2>
          <div className="friend-list">
            {receivedRequests.map((request) => (
              <FriendProfile
                key={request.requestId}
                nickname={request.requesterNickname}
                profileImageUrl={request.requesterProfileImageUrl}
                actions={(
                  <span className="friend-request-actions">
                    <button type="button" onClick={() => handleAccept(request.requestId)}>수락</button>
                    <button type="button" onClick={() => handleReject(request.requestId)}>거절</button>
                  </span>
                )}
              />
            ))}
          </div>
        </section>
      )}

      {/* ===== 실제 친구 API의 로딩·오류·목록 상태 표시 ===== */}
      <section className="friend-section friend-section--list" aria-labelledby="friend-list-title">
        <h2 id="friend-list-title">친구 {visibleFriends.length}</h2>
        {isLoading ? (
          <p className="main-empty friend-empty">친구를 불러오는 중이에요.</p>
        ) : errorMessage ? (
          <div className="main-request-state friend-empty" role="alert">
            <p>{errorMessage}</p>
            <button type="button" onClick={retryFriends}>다시 시도</button>
          </div>
        ) : visibleFriends.length > 0 ? (
          <div className="friend-list">
            {visibleFriends.map((friend) => (
              <FriendProfile
                key={friend.id}
                nickname={friend.nickname}
                profileImageUrl={friend.profileImageUrl}
              />
            ))}
          </div>
        ) : (
          <p className="main-empty friend-empty">친구를 UID로 추가해보세요.</p>
        )}
      </section>
    </MainScreen>
  )
}

export default FriendListPage
