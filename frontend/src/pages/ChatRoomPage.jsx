import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import ChatHeader from '../components/chat/ChatHeader'
import ChatNotice from '../components/chat/ChatNotice'
import ChatMessageList from '../components/chat/ChatMessageList'
import ChatInput from '../components/chat/ChatInput'
import MomeokjiPage from './MomeokjiPage'
import ParticipantPreferencePage from './ParticipantPreferencePage'
import MomeokjiPreferenceNotice from '../components/momeokji/MomeokjiPreferenceNotice'
import MomeokjiVoteNotice from '../components/momeokji/MomeokjiVoteNotice'
import MomeokjiVotePage from '../components/momeokji/MomeokjiVotePage'
import { getChatRoomMembers, getRecentMessages, seedDevChat } from '../services/chatApi'
import { connectChatSocket, disconnectChatSocket, sendChatMessage } from '../services/chatSocket'
import { createMeetup } from '../services/meetupService'
import { replaceVotes, submitMyPreference } from '../services/meetupApi'
import { recommendRestaurants } from '../services/momeokjiService'
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
  // 실제 API의 chatRoomId가 Long 타입이므로 목업에서도 숫자 ID를 사용합니다.
  id: 1,
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

// ===== API가 내려준 사용자용 오류 문구를 우선 표시 =====
function getErrorMessage(error, fallbackMessage) {
  return error?.userMessage
    || (error instanceof Error ? error.message : fallbackMessage)
}

// ===== 서버 채팅 메시지를 기존 말풍선 컴포넌트 형식으로 변환 =====
function toUiMessage(serverMessage, myMemberId) {
  return {
    id: serverMessage.id,
    sender: serverMessage.memberId === myMemberId ? 'me' : 'other',
    name: serverMessage.nickname,
    text: serverMessage.content,
    time: new Date(serverMessage.createdAt).toLocaleTimeString('ko-KR', {
      hour: 'numeric',
      minute: '2-digit',
    }),
  }
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

// ===== 백엔드 RoundResponse를 기존 투표 UI가 사용하는 세션 형태로 변환 =====
function toServerVoteSession(round, settings, previousSession = null) {
  if (!round?.roundId || !settings) return previousSession

  const candidates = round.candidates ?? []
  const recommendAgain = candidates.find((candidate) => (
    candidate.candidateType === 'RECOMMEND_AGAIN'
  ))
  const restaurantCandidates = candidates.filter((candidate) => (
    candidate.candidateType !== 'RECOMMEND_AGAIN'
  ))
  const votes = Object.fromEntries(restaurantCandidates.map((candidate) => [
    candidate.roundCandidateId,
    candidate.voterIds ?? [],
  ]))
  votes[RECOMMEND_AGAIN_ID] = recommendAgain?.voterIds ?? []

  const isNewRound = previousSession?.roundId !== round.roundId
  const createdAt = isNewRound ? new Date() : new Date(previousSession.createdAt)
  const deadlineAt = isNewRound
    ? new Date(createdAt.getTime() + settings.voteDurationMinutes * 60_000)
    : new Date(previousSession.deadlineAt)

  return {
    id: `${round.meetupId}-${round.roundId}`,
    source: 'server',
    meetupId: round.meetupId,
    roundId: round.roundId,
    recommendAgainCandidateId: recommendAgain?.roundCandidateId ?? null,
    status: round.votedParticipantCount > 0 ? 'IN_PROGRESS' : 'CREATED',
    settings,
    recommendations: restaurantCandidates.map((candidate) => ({
      id: candidate.roundCandidateId,
      name: candidate.name,
      menuName: candidate.category,
      priceRange: candidate.category || '추천 메뉴',
      address: candidate.roadAddress || candidate.address || '',
      latitude: candidate.latitude,
      longitude: candidate.longitude,
      reason: candidate.reason,
      imageUrl: candidate.imageUrl || '',
      visual: '🍽️',
    })),
    createdAt: createdAt.toISOString(),
    deadlineAt: deadlineAt.toISOString(),
    votes,
    participantCount: round.participantCount,
    votedParticipantCount: round.votedParticipantCount,
    excludedRestaurantIds: previousSession?.excludedRestaurantIds ?? [],
    generation: Math.max(0, (round.roundNo ?? 1) - 1),
    voteRound: round.roundNo ?? 1,
    tieRetryCount: Math.max(0, (round.roundNo ?? 1) - 1),
  }
}

// ===== 개인 조건 화면 값을 백엔드 PreferenceSubmitRequest 규격으로 정리 =====
function toPreferenceRequest(preference, settings) {
  const preferredCategories = (settings.menus ?? []).filter((menu) => menu !== '아무거나')
  return {
    walkMinutes: 10,
    preferredCategories: preferredCategories.length > 0
      ? preferredCategories
      : [settings.themeLabel || '음식점'],
    budgetLimit: preference.budgetLimit,
    parkingNeeded: preference.parkingPreference === 'REQUIRED',
    excludedFoods: preference.excludedFoods ?? [],
    atmosphere: (preference.moodPreferences ?? [])
      .filter((mood) => mood !== '상관없어요')
      .join(', ') || null,
  }
}

// ===== 최종 공지 이벤트를 기존 결과 화면 데이터로 변환 =====
function toServerResult(finalNotice, settings, recommendations = []) {
  const matchedRecommendation = recommendations.find((restaurant) => (
    restaurant.name === finalNotice.restaurantName
  ))
  return {
    ...settings,
    selectedRestaurant: {
      id: `final-${finalNotice.restaurantName}`,
      name: finalNotice.restaurantName,
      menuName: matchedRecommendation?.menuName || matchedRecommendation?.priceRange || '',
      address: finalNotice.roadAddress || finalNotice.address || '',
      latitude: finalNotice.latitude,
      longitude: finalNotice.longitude,
      imageUrl: finalNotice.imageUrl || matchedRecommendation?.imageUrl || '',
    },
    decisionMethod: 'SERVER_RESULT',
  }
}

// ===== 다른 참가자가 받은 모임 초대 이벤트를 개인 조건 입력 화면 설정으로 복원 =====
function toInvitationSettings(event) {
  const meetup = event.meetup
  const commonOption = meetup.commonOption
  const meetingTime = String(commonOption.meetingTime || '')
  const participantIds = (event.participants ?? []).map((participant) => participant.memberId)
  return {
    meetupId: meetup.meetupId,
    date: meetingTime.slice(0, 10),
    time: meetingTime.slice(11, 16),
    timeZone: 'Asia/Seoul',
    timeLabel: meetingTime.slice(11, 16),
    place: {
      name: commonOption.destinationName,
      address: commonOption.destinationName,
      latitude: commonOption.destinationLatitude,
      longitude: commonOption.destinationLongitude,
    },
    participantIds,
    participantNames: (event.participants ?? []).map((participant) => participant.nickname),
    personalOptionDurationMinutes: 10,
    voteDurationMinutes: 10,
    themeCode: commonOption.purpose,
    themeLabel: commonOption.purpose,
    menus: [],
    avoidFoods: [],
    moods: [],
    participantPreferenceDeadlineAt: new Date(Date.now() + 10 * 60_000).toISOString(),
  }
}

function ChatRoomPage({ room: providedRoom, currentUser = DEMO_CURRENT_USER }) {
  // ===== URL의 채팅방 ID를 실제 API 요청용 숫자 ID로 연결 =====
  const { roomId } = useParams()
  const navigate = useNavigate()
  const numericRoomId = Number(roomId)
  const room = providedRoom ?? {
    ...DEMO_ROOM,
    id: Number.isFinite(numericRoomId) && numericRoomId > 0 ? numericRoomId : DEMO_ROOM.id,
    members: [
      currentUser,
      ...DEMO_ROOM.members.filter((member) => member.id !== DEMO_CURRENT_USER.id),
    ],
  }

  const useMockApi = String(import.meta.env.VITE_USE_MOCK ?? 'false').toLowerCase() === 'true'
  const [chatMembers, setChatMembers] = useState([])
  const [chatConnectionError, setChatConnectionError] = useState('')
  const chatSocketRef = useRef(null)
  const pendingMeetingSettingsRef = useRef(null)
  const voteSessionRef = useRef(null)
  const voteMessageMeetupIdsRef = useRef(new Set())

  // 실제 연결 시 서버 회원을 사용하고, 목업 모드에서는 기존 고정 참가자를 유지합니다.
  const roomParticipants = chatMembers.length > 0
    ? chatMembers.map((member) => ({ id: member.id, name: member.nickname }))
    : room.members
  // API 연결 후에는 createInitialMessages 대신 채팅 조회 응답으로 초기화
  const [messages, setMessages] = useState(createInitialMessages)
  const [isMomeokjiOpen, setIsMomeokjiOpen] = useState(false)
  const [isParticipantPreferenceOpen, setIsParticipantPreferenceOpen] = useState(false)
  const [isVotePageOpen, setIsVotePageOpen] = useState(false)
  const [isCreatingMeetup, setIsCreatingMeetup] = useState(false)
  const [isCreatingVote, setIsCreatingVote] = useState(false)
  const [isResolvingVote, setIsResolvingVote] = useState(false)
  const [meetupCreationError, setMeetupCreationError] = useState('')
  const [recommendationError, setRecommendationError] = useState('')
  const [pendingMeetingSettings, setPendingMeetingSettings] = useState(null)
  const [preferenceSession, setPreferenceSession] = useState(null)
  const [voteSession, setVoteSession] = useState(null)
  const [momeokjiResult, setMomeokjiResult] = useState(null)
  const voteSubmissionLockRef = useRef(false)
  const canViewVote = voteSession?.settings.participantIds.includes(currentUser.id)
  const canViewPreference = preferenceSession?.participantIds.includes(currentUser.id)
  const canViewMomeokjiResult = momeokjiResult?.participantIds.includes(currentUser.id)

  useEffect(() => {
    pendingMeetingSettingsRef.current = pendingMeetingSettings
  }, [pendingMeetingSettings])

  useEffect(() => {
    voteSessionRef.current = voteSession
  }, [voteSession])

  // ===== 서버에서 첫 추천 회차가 열리면 채팅에 투표 진입 버블을 한 번만 추가 =====
  useEffect(() => {
    if (voteSession?.source !== 'server' || voteMessageMeetupIdsRef.current.has(voteSession.meetupId)) {
      return
    }
    voteMessageMeetupIdsRef.current.add(voteSession.meetupId)
    setMessages((previous) => [
      ...previous,
      {
        id: `momeokji-vote-${voteSession.meetupId}`,
        type: 'MOMEOKJI_VOTE',
        voteSessionId: voteSession.id,
        sender: 'me',
        time: currentTime(),
      },
    ])
  }, [voteSession])

  // ===== 채팅 이력 조회 후 STOMP를 구독해 새 메시지를 실시간으로 추가 =====
  useEffect(() => {
    if (useMockApi || !room.id || !currentUser?.id) return undefined

    let cancelled = false
    const connectChat = async () => {
      try {
        let [history, members] = await Promise.all([
          getRecentMessages(room.id),
          getChatRoomMembers(room.id),
        ])
        if (cancelled) return

        // 개발 환경의 빈 방만 실제 백엔드에 예시 대화를 저장해 모먹지 흐름을 바로 시험합니다.
        if (import.meta.env.DEV && history.length === 0) {
          try {
            history = await seedDevChat(room.id)
            members = await getChatRoomMembers(room.id)
          } catch {
            // dev 시드 API가 없는 서버에서도 빈 채팅방 자체는 정상적으로 사용할 수 있습니다.
          }
        }

        setMessages(history.map((message) => toUiMessage(message, currentUser.id)))
        setChatMembers(members)
        chatSocketRef.current = await connectChatSocket(room.id, {
          onMessage: (message) => {
            setMessages((previous) => [...previous, toUiMessage(message, currentUser.id)])
          },
          onInvitation: (event) => {
            const settings = toInvitationSettings(event)
            if (!settings.participantIds.includes(currentUser.id)) return
            pendingMeetingSettingsRef.current = settings
            setPendingMeetingSettings(settings)
            setPreferenceSession({
              meetupId: settings.meetupId,
              status: 'IN_PROGRESS',
              participantIds: settings.participantIds,
              submittedParticipantIds: [],
              deadlineAt: settings.participantPreferenceDeadlineAt,
            })
            setIsParticipantPreferenceOpen(true)
          },
          onProgress: (event) => {
            setPreferenceSession((previous) => {
              if (!previous || previous.meetupId !== event.meetupId) return previous
              return {
                ...previous,
                submittedParticipantIds: (event.participants ?? [])
                  .filter((participant) => participant.submissionStatus === 'SUBMITTED')
                  .map((participant) => participant.memberId),
              }
            })
          },
          onRecommendationProgress: (event) => {
            if (event.status === 'STARTED') {
              setIsCreatingVote(true)
              return
            }
            if (event.status === 'FAILED') {
              setIsCreatingVote(false)
              setRecommendationError(event.errorMessage || '추천 가게를 불러오지 못했어요.')
              return
            }
            if (event.status === 'COMPLETED' && event.result) {
              // 이전 추천 실패 공지가 남아 있어도 성공 회차가 열리면 즉시 제거합니다.
              setRecommendationError('')
              setVoteSession((previous) => toServerVoteSession(
                event.result,
                previous?.settings ?? pendingMeetingSettingsRef.current,
                previous,
              ))
              setPreferenceSession(null)
              setIsCreatingVote(false)
            }
          },
          onVoteUpdate: (round) => {
            // 정상 투표 회차 수신이 이전 실패 상태보다 최신 상태입니다.
            setRecommendationError('')
            setVoteSession((previous) => toServerVoteSession(
              round,
              previous?.settings ?? pendingMeetingSettingsRef.current,
              previous,
            ))
          },
          onFinalNotice: (finalNotice) => {
            const settings = voteSessionRef.current?.settings ?? pendingMeetingSettingsRef.current
            if (!settings) return
            setMomeokjiResult(toServerResult(
              finalNotice,
              settings,
              voteSessionRef.current?.recommendations,
            ))
            setVoteSession((previous) => previous ? { ...previous, status: 'CLOSED' } : previous)
          },
        })
      } catch (error) {
        if (!cancelled) {
          setChatConnectionError(error instanceof Error
            ? error.message
            : '채팅 서버에 연결하지 못했습니다.')
        }
      }
    }

    connectChat()
    return () => {
      cancelled = true
      disconnectChatSocket(chatSocketRef.current)
      chatSocketRef.current = null
    }
  }, [currentUser?.id, room.id, useMockApi])

  // ===== 투표 생성 시각부터 설정된 제한시간이 지나면 현재 득표로 자동 결정 =====
  useEffect(() => {
    if (voteSession?.source === 'server'
      || !voteSession?.deadlineAt
      || ['CLOSED', 'EXPIRED'].includes(voteSession.status)) {
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
    if (chatSocketRef.current?.connected) {
      sendChatMessage(chatSocketRef.current, room.id, text)
      return
    }

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

  // ===== 주최자 공통 설정을 서버에 저장한 뒤 개인 조건 입력 단계로 전환 =====
  const requestParticipantPreference = async (settings) => {
    if (isCreatingMeetup) return

    setIsCreatingMeetup(true)
    setMeetupCreationError('')
    setRecommendationError('')

    try {
      // 투표 마감은 추천 회차가 열린 시점부터 계산해야 하므로 최초 모임 생성에는 보내지 않습니다.
      const meetup = await createMeetup({
        chatRoomId: room.id,
        settings,
      })
      const meetupId = meetup.id ?? meetup.meetupId
      if (meetupId == null) throw new Error('서버가 모임 ID를 반환하지 않았습니다.')

      const isCurrentUserSelected = settings.participantIds.includes(currentUser.id)
      const participantPreferenceDeadlineAt = new Date(
        Date.now() + settings.personalOptionDurationMinutes * 60_000,
      ).toISOString()
      const savedSettings = {
        ...settings,
        meetupId,
        participantPreferenceDeadlineAt,
      }

      pendingMeetingSettingsRef.current = savedSettings
      setPendingMeetingSettings(savedSettings)
      setPreferenceSession({
        meetupId,
        status: 'IN_PROGRESS',
        participantIds: settings.participantIds,
        submittedParticipantIds: [],
        deadlineAt: participantPreferenceDeadlineAt,
      })
      // 선택된 참가자의 클라이언트에서만 개인 조건 입력 시트를 엽니다.
      setIsParticipantPreferenceOpen(isCurrentUserSelected)
    } catch (error) {
      setMeetupCreationError(
        getErrorMessage(error, '모임을 만들지 못했습니다. 다시 시도해주세요.'),
      )
    } finally {
      setIsCreatingMeetup(false)
    }
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
  const submitParticipantPreference = async (preference) => {
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

    if (useMockApi) {
      setPendingMeetingSettings(null)
      createVoteSession(settings)
      return
    }

    setIsCreatingVote(true)
    setRecommendationError('')
    try {
      const response = await submitMyPreference(
        settings.meetupId,
        toPreferenceRequest(preference, settings),
      )
      if (response.round) {
        setRecommendationError('')
        setVoteSession((previous) => toServerVoteSession(response.round, settings, previous))
        setPreferenceSession(null)
      } else {
        setPreferenceSession((previous) => previous ? {
          ...previous,
          status: 'IN_PROGRESS',
        } : previous)
      }
    } catch (error) {
      setRecommendationError(
        getErrorMessage(error, '개인 조건을 저장하지 못했습니다.'),
      )
    } finally {
      setIsCreatingVote(false)
    }
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
    if (isCreatingMeetup) return
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
      // ===== 실제 서버 회차는 네 후보의 선택 전체를 한 번에 교체하고 응답/웹소켓으로 동기화 =====
      if (!useMockApi && voteSession.source === 'server') {
        const candidateIds = uniqueOptionIds.map((optionId) => (
          optionId === RECOMMEND_AGAIN_ID
            ? voteSession.recommendAgainCandidateId
            : Number(optionId)
        ))
        if (candidateIds.some((candidateId) => !Number.isFinite(candidateId))) {
          throw new Error('서버의 재투표 후보 정보를 찾지 못했습니다.')
        }

        setIsResolvingVote(true)
        setRecommendationError('')
        try {
          const round = await replaceVotes(voteSession.meetupId, voteSession.roundId, candidateIds)
          setVoteSession((previous) => previous?.status === 'CLOSED'
            ? previous
            : toServerVoteSession(
              round,
              previous?.settings ?? pendingMeetingSettingsRef.current,
              previous,
            ))
        } catch (error) {
          setRecommendationError(
            getErrorMessage(error, '투표를 저장하지 못했습니다.'),
          )
        } finally {
          setIsResolvingVote(false)
        }
        return
      }

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
      <ChatHeader
        roomName="진원버스 가즈아"
        memberCount={roomParticipants.length}
        onBack={() => navigate('/chats')}
      />

      <div className="chat-body">
        {chatConnectionError && <ChatNotice text={chatConnectionError} />}
        {/* ===== 모임 생성 REST 요청 상태를 채팅 공지로 표시 ===== */}
        {isCreatingMeetup && <ChatNotice text="모임을 만들고 있어요." />}
        {meetupCreationError && <ChatNotice text={meetupCreationError} />}

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
        {/* 성공한 투표 회차가 존재하면 이전 추천 실패 공지는 표시하지 않습니다. */}
        {recommendationError && !voteSession && <ChatNotice text={recommendationError} />}

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
