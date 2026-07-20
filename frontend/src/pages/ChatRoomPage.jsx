import { useEffect, useRef, useState } from 'react'
import ChatHeader from '../components/chat/ChatHeader'
import ChatNotice from '../components/chat/ChatNotice'
import ChatMessageList from '../components/chat/ChatMessageList'
import ChatInput from '../components/chat/ChatInput'
import MomeokjiPage from './MomeokjiPage'
import ParticipantPreferencePage from './ParticipantPreferencePage'
import MomeokjiPreferenceNotice from '../components/momeokji/MomeokjiPreferenceNotice'
import MomeokjiVoteNotice from '../components/momeokji/MomeokjiVoteNotice'
import MomeokjiVotePage from '../components/momeokji/MomeokjiVotePage'
import { recommendRestaurants } from '../services/momeokjiApi'
import {
  createVoteCounts,
  findWinningRestaurant,
  hasEveryoneVoted,
  hasRecommendAgainWon,
  RECOMMEND_AGAIN_ID,
} from '../utils/momeokjiVote'
import './ChatRoomPage.css'

const DEMO_CURRENT_USER = { id: 'member-me', name: '나' }
const DEMO_ROOM = {
  id: 'room-demo',
  members: [
    DEMO_CURRENT_USER,
    { id: 'member-seojun', name: '서준' },
    { id: 'member-gyeongjun', name: '경준' },
  ],
}

function currentTime() {
  return new Date().toLocaleTimeString('ko-KR', {
    hour: 'numeric',
    minute: '2-digit',
  })
}

// 채팅방을 처음 열었을 때의 시각으로 기본 대화를 생성.
function createInitialMessages() {
  const startedAt = currentTime()

  return [
    { id: 1, sender: 'me', text: '오늘 모먹지??', time: startedAt },
    {
      id: 2,
      sender: 'other',
      name: '서준',
      text: '저는 상관없어요 아무거나?',
      time: startedAt,
    },
    {
      id: 3,
      sender: 'me',
      text: '또 아무거나야? 좀골라봐바',
      time: startedAt,
    },
    { id: 4, 
    sender: 'other',
     name: '경준', 
     text: '모 먹지 써볼까요그럼?', 
     time: startedAt },
  ]
}

// ===== 공지사항에 표시할 투표 진행 단계 문구 =====
function getVoteNoticeText(status) {
  if (status === 'CLOSED') return '투표 결과를 확인해요.'
  if (status === 'EXPIRED') return '투표 시간이 만료됐어요.'
  if (status === 'IN_PROGRESS') return '투표가 진행 중이에요.'
  return '투표가 만들어졌어요.'
}

// ===== 공지사항에 표시할 개인 조건 수집 단계 문구 =====
function getPreferenceNoticeText(status) {
  if (status === 'GENERATING') return 'AI가 추천 식당을 찾고 있어요.'
  return '참가자 조건 입력이 시작됐어요.'
}

function ChatRoomPage({ room = DEMO_ROOM, currentUser = DEMO_CURRENT_USER }) {
  // API 연결 후 room.members에 채팅방 참가자 DTO(id, name)를 그대로 전달합니다.
  const roomParticipants = room.members
  // API 연결 후에는 createInitialMessages 대신 채팅 조회 응답으로 초기화
  const [messages, setMessages] = useState(createInitialMessages)
  const [isMomeokjiOpen, setIsMomeokjiOpen] = useState(false)
  const [isParticipantPreferenceOpen, setIsParticipantPreferenceOpen] = useState(false)
  const [isVotePageOpen, setIsVotePageOpen] = useState(false)
  const [isCreatingVote, setIsCreatingVote] = useState(false)
  const [isResolvingVote, setIsResolvingVote] = useState(false)
  const [recommendationError, setRecommendationError] = useState('')
  const [pendingMeetingSettings, setPendingMeetingSettings] = useState(null)
  const [preferenceSession, setPreferenceSession] = useState(null)
  const [voteSession, setVoteSession] = useState(null)
  const [momeokjiResult, setMomeokjiResult] = useState(null)
  const voteSubmissionLockRef = useRef(false)
  const canViewVote = voteSession?.settings.participantIds.includes(currentUser.id)
  const canViewPreference = preferenceSession?.participantIds.includes(currentUser.id)
  const canViewMomeokjiResult = momeokjiResult?.participantIds.includes(currentUser.id)

  // ===== 투표 생성 시각부터 설정된 제한시간이 지나면 현재 득표로 자동 결정 =====
  useEffect(() => {
    if (!voteSession?.deadlineAt || ['CLOSED', 'EXPIRED'].includes(voteSession.status)) {
      return undefined
    }

    const remainingMilliseconds = Math.max(
      0,
      new Date(voteSession.deadlineAt).getTime() - Date.now(),
    )
    const timeoutId = window.setTimeout(() => {
      const votedParticipantIds = new Set(Object.values(voteSession.votes).flat())
      const voteCounts = createVoteCounts(voteSession.recommendations, voteSession.votes)

      if (votedParticipantIds.size === 0) {
        setMomeokjiResult({
          ...voteSession.settings,
          selectedRestaurant: null,
          voteCounts,
          decisionMethod: 'NO_VOTES_TIMEOUT',
        })
        setVoteSession((previous) => (
          previous?.id === voteSession.id ? { ...previous, status: 'EXPIRED' } : previous
        ))
        return
      }

      const restaurantVoteCounts = voteSession.recommendations.map((restaurant) => (
        voteSession.votes[restaurant.id]?.length ?? 0
      ))
      const highestRestaurantVoteCount = Math.max(...restaurantVoteCounts)
      const restaurantLeaderCount = restaurantVoteCounts.filter((count) => (
        count === highestRestaurantVoteCount
      )).length
      const winner = findWinningRestaurant(voteSession.recommendations, voteSession.votes)

      setMomeokjiResult({
        ...voteSession.settings,
        selectedRestaurant: winner,
        voteCounts,
        decisionMethod: restaurantLeaderCount > 1
          ? 'DEADLINE_RANDOM_RESTAURANT_TIE'
          : 'DEADLINE_MAJORITY',
      })
      setVoteSession((previous) => (
        previous?.id === voteSession.id ? { ...previous, status: 'CLOSED' } : previous
      ))
    }, remainingMilliseconds)

    return () => window.clearTimeout(timeoutId)
  }, [voteSession])

  const sendMessage = (text) => {
    setMessages((previous) => [
      ...previous,
      {
        id: crypto.randomUUID(),
        sender: 'me',
        text,
        time: currentTime(),
      },
    ])
  }

  // ===== 주최자 공통 설정 완료 후 현재 참가자의 개인 조건 입력 단계로 전환 =====
  const requestParticipantPreference = (settings) => {
    const isCurrentUserSelected = settings.participantIds.includes(currentUser.id)
    const participantPreferenceDeadlineAt = new Date(
      Date.now() + settings.personalOptionDurationMinutes * 60_000,
    ).toISOString()
    setPendingMeetingSettings({ ...settings, participantPreferenceDeadlineAt })
    setPreferenceSession({
      status: 'IN_PROGRESS',
      participantIds: settings.participantIds,
      submittedParticipantIds: [],
      deadlineAt: participantPreferenceDeadlineAt,
    })
    setRecommendationError('')
    // 선택된 참가자의 클라이언트에서만 개인 조건 입력 시트를 엽니다.
    setIsParticipantPreferenceOpen(isCurrentUserSelected)
  }

  // ===== 8단계 설정 완료 후 AI 추천 3곳으로 투표 세션 생성 =====
  const createVoteSession = async (settings) => {
    setIsCreatingVote(true)
    setPreferenceSession((previous) => (
      previous ? { ...previous, status: 'GENERATING' } : previous
    ))
    setRecommendationError('')
    setMomeokjiResult(null)
    setVoteSession(null)

    try {
      const recommendations = await recommendRestaurants(settings)
      const sessionId = crypto.randomUUID()
      const createdAt = new Date()
      const deadlineAt = new Date(
        createdAt.getTime() + settings.voteDurationMinutes * 60_000,
      )
      setVoteSession({
        id: sessionId,
        status: 'CREATED',
        settings,
        recommendations,
        createdAt: createdAt.toISOString(),
        deadlineAt: deadlineAt.toISOString(),
        votes: {},
        excludedRestaurantIds: [],
        generation: 0,
        voteRound: 1,
        tieRetryCount: 0,
      })
      // 설정 완료와 동시에 일반 텍스트와 구분되는 투표 기능 버블을 생성합니다.
      setMessages((previous) => [
        ...previous,
        {
          id: crypto.randomUUID(),
          type: 'MOMEOKJI_VOTE',
          voteSessionId: sessionId,
          sender: 'me',
          time: currentTime(),
        },
      ])
      setPreferenceSession(null)
    } catch {
      setPreferenceSession(null)
      setRecommendationError('추천 가게를 불러오지 못했어요. 다시 시도해주세요.')
    } finally {
      setIsCreatingVote(false)
    }
  }

  // ===== 개인 조건 DTO를 공통 설정에 합쳐 기존 추천 API 요청으로 전달 =====
  const submitParticipantPreference = (preference) => {
    if (!pendingMeetingSettings) return

    const settings = {
      ...pendingMeetingSettings,
      participantPreferences: [preference],
    }
    setPreferenceSession((previous) => {
      if (!previous) return previous
      const submittedParticipantIds = new Set(previous.submittedParticipantIds)
      submittedParticipantIds.add(currentUser.id)
      return {
        ...previous,
        status: 'GENERATING',
        submittedParticipantIds: [...submittedParticipantIds],
      }
    })
    setIsParticipantPreferenceOpen(false)
    setPendingMeetingSettings(null)
    createVoteSession(settings)
  }

  // ===== 목업 단계의 참여 거절 처리: 추천을 시작하지 않고 채팅 공지로 안내 =====
  const declineParticipantPreference = () => {
    setIsParticipantPreferenceOpen(false)
    setPendingMeetingSettings(null)
    setPreferenceSession(null)
    setRecommendationError('참여 안 하기를 선택했어요. 모임 설정을 다시 시작해주세요.')
  }

  // ===== 모먹지 기능 버튼은 진행 상태에 맞는 화면을 엽니다. =====
  const openCurrentMomeokjiStage = () => {
    if (voteSession && canViewVote) {
      setIsVotePageOpen(true)
      return
    }
    if (pendingMeetingSettings) {
      if (canViewPreference) setIsParticipantPreferenceOpen(true)
      return
    }
    setIsMomeokjiOpen(true)
  }

  // ===== 최다 득표 가게 또는 가게 공동 1등 중 무작위 결과로 투표 마감 =====
  const closeVoteWithWinner = (winner, updatedVotes, decisionMethod) => {
    if (!winner || !voteSession) return

    setMomeokjiResult({
      ...voteSession.settings,
      selectedRestaurant: winner,
      voteCounts: createVoteCounts(voteSession.recommendations, updatedVotes),
      decisionMethod,
    })
    setVoteSession((previous) => ({ ...previous, status: 'CLOSED', votes: updatedVotes }))
  }

  // ===== 한 사람이 선택한 최대 4개 표를 저장하고 전원 참여 후 다수결 처리 =====
  const submitVote = async (selectedOptionIds) => {
    if (!voteSession || isResolvingVote || voteSubmissionLockRef.current) return
    const uniqueOptionIds = [...new Set(selectedOptionIds)]
    const validOptionIds = new Set([
      ...voteSession.recommendations.map((restaurant) => restaurant.id),
      RECOMMEND_AGAIN_ID,
    ])
    if (
      uniqueOptionIds.length === 0
      || uniqueOptionIds.length > 4
      || uniqueOptionIds.some((optionId) => !validOptionIds.has(optionId))
    ) return
    voteSubmissionLockRef.current = true

    try {
      const updatedVotes = Object.fromEntries(
        voteSession.recommendations.map((restaurant) => [
          restaurant.id,
          (voteSession.votes[restaurant.id] ?? []).filter((participantId) => (
            participantId !== currentUser.id
          )),
        ]),
      )
      updatedVotes[RECOMMEND_AGAIN_ID] = (voteSession.votes[RECOMMEND_AGAIN_ID] ?? [])
        .filter((participantId) => participantId !== currentUser.id)
      // 기존 표를 먼저 제거한 뒤 현재 선택을 기록해 수정 투표도 중복 없이 교체합니다.
      uniqueOptionIds.forEach((optionId) => {
        updatedVotes[optionId] = [...updatedVotes[optionId], currentUser.id]
      })

      if (!hasEveryoneVoted(voteSession.settings.participantIds, updatedVotes)) {
        setVoteSession((previous) => ({
          ...previous,
          status: 'IN_PROGRESS',
          votes: updatedVotes,
        }))
        // 남은 참가자가 있으면 투표 화면을 유지해 게이지와 남은 인원을 즉시 표시합니다.
        return
      }

      const shouldRecommendAgain = hasRecommendAgainWon(
        voteSession.recommendations,
        updatedVotes,
      )

      // ===== 재투표가 단독 1위일 때만 기존 후보를 누적 제외하고 새 가게 3곳 요청 =====
      if (shouldRecommendAgain) {
        setIsResolvingVote(true)
        setRecommendationError('')
        const excludedRestaurantIds = [
          ...new Set([
            ...voteSession.excludedRestaurantIds,
            ...voteSession.recommendations.map((restaurant) => restaurant.id),
          ]),
        ]
        const generation = voteSession.generation + 1

        try {
          const recommendations = await recommendRestaurants(voteSession.settings, {
            excludeRestaurantIds: excludedRestaurantIds,
            generation,
          })
          setVoteSession((previous) => ({
            ...previous,
            status: 'CREATED',
            recommendations,
            votes: {},
            excludedRestaurantIds,
            generation,
            voteRound: previous.voteRound + 1,
            tieRetryCount: previous.tieRetryCount + 1,
          }))
        } catch {
          // 새 후보 조회 실패 시 현재 후보를 유지하고 해당 라운드의 표만 초기화합니다.
          setRecommendationError('새 후보를 불러오지 못했어요. 현재 후보로 다시 투표해주세요.')
          setVoteSession((previous) => ({
            ...previous,
            status: 'CREATED',
            votes: {},
            excludedRestaurantIds,
            generation,
            voteRound: previous.voteRound + 1,
            tieRetryCount: previous.tieRetryCount + 1,
          }))
        } finally {
          setIsResolvingVote(false)
          setIsVotePageOpen(false)
        }
        return
      }

      // ===== 재투표와 가게가 공동 1등이면 가게를 우선하고, 가게끼리 동률이면 무작위 확정 =====
      const restaurantVoteCounts = voteSession.recommendations.map((restaurant) => (
        updatedVotes[restaurant.id]?.length ?? 0
      ))
      const highestRestaurantVoteCount = Math.max(...restaurantVoteCounts)
      const restaurantLeaderCount = restaurantVoteCounts.filter((count) => (
        count === highestRestaurantVoteCount
      )).length
      closeVoteWithWinner(
        findWinningRestaurant(voteSession.recommendations, updatedVotes),
        updatedVotes,
        restaurantLeaderCount > 1 ? 'RANDOM_RESTAURANT_TIE' : 'MAJORITY',
      )

      setIsVotePageOpen(false)
    } finally {
      voteSubmissionLockRef.current = false
    }
  }

  return (
    <div className="chat-room">
      {/* 필요 없는 영역은 이 조립부에서 컴포넌트 한 줄만 제거. */}
      {/* ===== 채팅방 참가자 수: 이후 room.members API 데이터와 자동 연동 ===== */}
      <ChatHeader roomName="진원버스 가즈아" memberCount={room.members.length} />

      <div className="chat-body">
        {/* ===== 참가자 전용 공지: 개인 조건 입력 현황과 현재 단계 연결 ===== */}
        {preferenceSession && canViewPreference && (
          <ChatNotice
            text={getPreferenceNoticeText(preferenceSession.status)}
          >
            <MomeokjiPreferenceNotice
              status={preferenceSession.status}
              participantCount={preferenceSession.participantIds.length}
              submittedCount={preferenceSession.submittedParticipantIds.length}
              deadlineAt={preferenceSession.deadlineAt}
              hasSubmitted={preferenceSession.submittedParticipantIds.includes(currentUser.id)}
              onOpen={() => setIsParticipantPreferenceOpen(true)}
            />
          </ChatNotice>
        )}

        {/* 개인 조건 단계 없이 추천을 다시 요청하는 예외 흐름의 생성 상태. */}
        {isCreatingVote && !preferenceSession && (
          <ChatNotice text="AI가 추천 식당을 찾고 있어요." />
        )}
        {recommendationError && <ChatNotice text={recommendationError} />}

        {/* ===== 참가자 전용 공지: 생성·진행·결과 상태와 연결 버튼만 표시 ===== */}
        {voteSession && canViewVote && (
          <ChatNotice text={getVoteNoticeText(voteSession.status)}>
            <MomeokjiVoteNotice
              status={voteSession.status}
              onOpenVote={() => setIsVotePageOpen(true)}
            />
          </ChatNotice>
        )}
        <ChatMessageList
          messages={messages}
          voteSession={voteSession}
          onOpenVote={() => setIsVotePageOpen(true)}
        />
      </div>

      {/* 이후 기능 버튼이나 모먹지 패널을 붙일 기준이 되는 하단 영역. */}
      <footer className="chat-input-area">
        <div className="chat-input-row">
          <button className="plus-button" type="button" aria-label="채팅 기능 열기">
            <span className="plus-icon" aria-hidden="true" />
          </button>
          <ChatInput
            onSend={sendMessage}
            onOpenMomeokji={openCurrentMomeokjiStage}
          />
        </div>
        <div className="home-bar" />
      </footer>

      <MomeokjiPage
        open={isMomeokjiOpen}
        onClose={() => setIsMomeokjiOpen(false)}
        onComplete={requestParticipantPreference}
        messages={messages}
        participants={roomParticipants}
        defaultParticipantIds={[currentUser.id]}
      />

      <ParticipantPreferencePage
        key={pendingMeetingSettings
          ? `${pendingMeetingSettings.date}-${pendingMeetingSettings.time}`
          : 'no-participant-preference'}
        open={Boolean(isParticipantPreferenceOpen && canViewPreference)}
        onClose={() => setIsParticipantPreferenceOpen(false)}
        onSubmit={submitParticipantPreference}
        onDecline={declineParticipantPreference}
        participant={currentUser}
        meetingSummary={pendingMeetingSettings
          ? `${pendingMeetingSettings.place.name} · ${pendingMeetingSettings.date} ${pendingMeetingSettings.timeLabel}`
          : ''}
        deadlineAt={pendingMeetingSettings?.participantPreferenceDeadlineAt}
      />

      <MomeokjiVotePage
        key={voteSession ? `${voteSession.id}-${voteSession.voteRound}` : 'no-vote'}
        open={isVotePageOpen}
        session={voteSession}
        currentUserId={currentUser.id}
        participants={roomParticipants}
        onClose={() => setIsVotePageOpen(false)}
        onSubmit={submitVote}
        isResolvingVote={isResolvingVote}
        result={canViewMomeokjiResult ? momeokjiResult : null}
      />
    </div>
  )
}

export default ChatRoomPage
