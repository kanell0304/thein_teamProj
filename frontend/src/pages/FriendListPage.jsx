import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import MainScreen from '../components/layout/MainScreen'
import { FriendAddIcon } from '../components/layout/HeaderActionIcons'
import useAuth from '../hooks/useAuth'
import { getMembers } from '../services/memberService'
import './FriendListPage.css'

function FriendProfile({ nickname, statusMessage, profileImageUrl, isMe = false }) {
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
  const currentNickname = user?.nickname || user?.name || '사용자'

  // ===== 친구 목록을 조회하고 실패 시 재시도 가능한 상태로 전환 =====
  const requestFriends = useCallback(async (signal) => {
    try {
      setFriends(await getMembers({ signal }))
    } catch (error) {
      if (signal?.aborted) return
      setErrorMessage(error.message)
    } finally {
      if (!signal?.aborted) setIsLoading(false)
    }
  }, [])

  // ===== 사용자가 오류 안내에서 친구 목록 조회를 다시 요청 =====
  const retryFriends = () => {
    setIsLoading(true)
    setErrorMessage('')
    requestFriends()
  }

  useEffect(() => {
    const controller = new AbortController()
    getMembers({ signal: controller.signal })
      .then(setFriends)
      .catch((error) => {
        if (!controller.signal.aborted) setErrorMessage(error.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })
    return () => controller.abort()
  }, [])

  // ===== 닉네임과 상태 메시지를 기준으로 사용자 검색 =====
  const visibleFriends = useMemo(() => {
    const keyword = searchText.trim().toLowerCase()
    if (!keyword) return friends
    return friends.filter((friend) => (
      `${friend.nickname} ${friend.statusMessage}`.toLowerCase().includes(keyword)
    ))
  }, [friends, searchText])

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
          {/* ===== 친구 추가 아이콘은 현재 회원 검색창으로 연결 ===== */}
          <button
            className="main-screen-action"
            type="button"
            aria-label="친구 추가"
            onClick={() => searchInputRef.current?.focus()}
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

      {/* ===== 실제 회원 API의 로딩·오류·목록 상태 표시 ===== */}
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
                statusMessage={friend.statusMessage}
                profileImageUrl={friend.profileImageUrl}
              />
            ))}
          </div>
        ) : (
          <p className="main-empty friend-empty">검색 결과가 없어요.</p>
        )}
      </section>
    </MainScreen>
  )
}

export default FriendListPage
