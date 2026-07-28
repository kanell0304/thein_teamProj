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
import { getChatRoomMembers, getRecentMessages } from '../services/chatApi'
import { fetchChatRoom } from '../api/chatRoomApi'
import { connectChatSocket, disconnectChatSocket, sendChatMessage } from '../services/chatSocket'
import { createMeetup } from '../services/meetupService'
import {
  getActiveMeetup,
  getMeetupParticipants,
  replaceVotes,
  submitMyPreference,
} from '../services/meetupApi'
import { RECOMMEND_AGAIN_ID } from '../utils/momeokjiVote'
import './ChatRoomPage.css'

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

// ===== API 응답 간 ID 타입 차이를 문자열 기준으로 정규화해 비교 =====
function includesMemberId(memberIds = [], memberId) {
  return memberIds.some((candidateId) => String(candidateId) === String(memberId))
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
  const deadlineAt = round.voteDeadlineAt
    ? new Date(round.voteDeadlineAt)
    : isNewRound
      ? new Date(createdAt.getTime() + settings.voteDurationMinutes * 60_000)
    : new Date(previousSession.deadlineAt)

  const serverStatus = round.meetupStatus === 'FINALIZED'
    ? 'CLOSED'
    : round.meetupStatus === 'EXPIRED'
      ? 'EXPIRED'
      : null

  return {
    id: `${round.meetupId}-${round.roundId}`,
    source: 'server',
    meetupId: round.meetupId,
    roundId: round.roundId,
    recommendAgainCandidateId: recommendAgain?.roundCandidateId ?? null,
    status: serverStatus ?? (round.votedParticipantCount > 0 ? 'IN_PROGRESS' : 'CREATED'),
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
  // 현재 추천 계약은 메뉴·카테고리·음식점명을 모두 카카오 검색용 선호 키워드 문자열로 집계합니다.
  const preferredCategories = [...new Set(
    (settings.menus ?? []).filter((menu) => menu !== '아무거나'),
  )]
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
    myDataConsent: preference.myDataConsent === true,
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

// ===== 서버 모임 상세와 참가자 목록을 개인 조건 입력 화면 설정으로 복원 =====
function toRestoredSettings(meetup, participants = []) {
  const commonOption = meetup.commonOption
  const meetingTime = String(commonOption.meetingTime || '')
  const participantIds = participants.map((participant) => participant.memberId)
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
    participantNames: participants.map((participant) => participant.nickname),
    personalOptionDurationMinutes: 10,
    voteDurationMinutes: meetup.voteDurationMinutes ?? 10,
    themeCode: commonOption.purpose,
    themeLabel: commonOption.purpose,
    menus: [],
    avoidFoods: [],
    moods: [],
    participantPreferenceDeadlineAt: meetup.personalOptionDeadlineAt,
  }
}

// ===== 다른 참가자가 실시간으로 받은 모임 초대도 조회 복원과 같은 형식으로 변환 =====
function toInvitationSettings(event) {
  return toRestoredSettings(event.meetup, event.participants ?? [])
}

function ChatRoomPage({ currentUser }) {
  // ===== URL의 채팅방 ID를 실제 API 요청용 숫자 ID로 연결 =====
  const { roomId } = useParams()
  const navigate = useNavigate()
  const numericRoomId = Number(roomId)
  const room = {
    id: Number.isFinite(numericRoomId) && numericRoomId > 0 ? numericRoomId : null,
  }

  const [chatMembers, setChatMembers] = useState([])
  const [roomInfo, setRoomInfo] = useState(null)
  const [chatConnectionError, setChatConnectionError] = useState('')
  const chatSocketRef = useRef(null)
  const pendingMeetingSettingsRef = useRef(null)
  const voteSessionRef = useRef(null)
  const voteMessageMeetupIdsRef = useRef(new Set())

  const roomParticipants = chatMembers.map((member) => ({ id: member.id, name: member.nickname }))
  // 서버에 저장된 채팅 이력만 표시하며 빈 방은 빈 목록으로 시작합니다.
  const [messages, setMessages] = useState([])
  const [isMomeokjiOpen, setIsMomeokjiOpen] = useState(false)
  const [momeokjiFeatureStartedAt, setMomeokjiFeatureStartedAt] = useState(null)
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
  const canViewVote = includesMemberId(voteSession?.settings.participantIds, currentUser.id)
  const canViewPreference = includesMemberId(preferenceSession?.participantIds, currentUser.id)
  const canViewMomeokjiResult = includesMemberId(momeokjiResult?.participantIds, currentUser.id)

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
    if (!room.id || !currentUser?.id) return undefined

    let cancelled = false
    const connectChat = async () => {
      try {
        const [history, members, fetchedRoom] = await Promise.all([
          getRecentMessages(room.id),
          getChatRoomMembers(room.id),
          fetchChatRoom(room.id),
        ])
        if (cancelled) return
        setRoomInfo(fetchedRoom)
        setChatConnectionError('')
        setMessages(history.map((message) => toUiMessage(message, currentUser.id)))
        setChatMembers(members)

        // 새 로그인·새로고침으로 초대 웹소켓을 놓쳐도 진행 중인 개인 조건 공지를 복원합니다.
        try {
          const activeMeetup = await getActiveMeetup(room.id)
          if (cancelled) return

          if (activeMeetup && ['PREFERENCE_COLLECTING', 'RECOMMENDING'].includes(activeMeetup.status)) {
            const meetupParticipants = await getMeetupParticipants(activeMeetup.meetupId)
            if (cancelled) return

            const settings = toRestoredSettings(activeMeetup, meetupParticipants)
            const isSelectedParticipant = includesMemberId(settings.participantIds, currentUser.id)
            const deadlineAt = settings.participantPreferenceDeadlineAt
            const isStillOpen = !deadlineAt || new Date(deadlineAt).getTime() > Date.now()

            if (isSelectedParticipant && (activeMeetup.status === 'RECOMMENDING' || isStillOpen)) {
              const submittedParticipantIds = meetupParticipants
                .filter((participant) => participant.submissionStatus === 'SUBMITTED')
                .map((participant) => participant.memberId)

              pendingMeetingSettingsRef.current = settings
              setPendingMeetingSettings(settings)
              setPreferenceSession({
                meetupId: activeMeetup.meetupId,
                status: activeMeetup.status === 'RECOMMENDING' ? 'GENERATING' : 'IN_PROGRESS',
                participantIds: settings.participantIds,
                submittedParticipantIds,
                deadlineAt,
              })
              // 공지는 복원하되 사용자가 버튼을 누르기 전까지 입력 시트는 닫아둡니다.
              setIsParticipantPreferenceOpen(false)
            }
          }
        } catch (error) {
          if (!cancelled) {
            setMeetupCreationError(getErrorMessage(
              error,
              '진행 중인 모임 정보를 불러오지 못했습니다.',
            ))
          }
        }

        chatSocketRef.current = await connectChatSocket(room.id, {
          onMessage: (message) => {
            setMessages((previous) => [...previous, toUiMessage(message, currentUser.id)])
          },
          onInvitation: (event) => {
            const settings = toInvitationSettings(event)
            if (!includesMemberId(settings.participantIds, currentUser.id)) return
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
              setPreferenceSession((previous) => previous ? {
                ...previous,
                status: 'GENERATING',
              } : previous)
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
  }, [currentUser?.id, room.id])

  const sendMessage = (text) => {
    if (!chatSocketRef.current?.connected) {
      setChatConnectionError('채팅 서버 연결이 끊겨 메시지를 보내지 못했습니다.')
      return false
    }

    setChatConnectionError('')
    sendChatMessage(chatSocketRef.current, room.id, text)
    return true
  }

  // ===== 주최자 공통 설정을 서버에 저장한 뒤 개인 조건 입력 단계로 전환 =====
  const requestParticipantPreference = async (settings) => {
    if (isCreatingMeetup) return

    setIsCreatingMeetup(true)
    setMeetupCreationError('')
    setRecommendationError('')

    try {
      // 투표 마감은 추천 회차가 열린 시점부터 계산해야 하므로 최초 모임 생성에는 보내지 않습니다.
      const participantPreferenceDeadlineAt = new Date(
        Date.now() + settings.personalOptionDurationMinutes * 60_000,
      ).toISOString()
      const meetup = await createMeetup({
        chatRoomId: room.id,
        settings,
        personalOptionDeadlineAt: participantPreferenceDeadlineAt,
      })
      const meetupId = meetup.id ?? meetup.meetupId
      if (meetupId == null) throw new Error('서버가 모임 ID를 반환하지 않았습니다.')

      const isCurrentUserSelected = includesMemberId(settings.participantIds, currentUser.id)
      const savedSettings = {
        ...settings,
        meetupId,
        participantPreferenceDeadlineAt,
      }

      // 새 모임이 서버에 생성된 뒤에만 이전 결과를 정리해 설정 취소 시 복귀할 수 있게 합니다.
      voteSessionRef.current = null
      setVoteSession(null)
      setMomeokjiResult(null)
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

  // ===== 공지 영역은 최신 진행 상태 하나만 표시하고 상태 변경 시 새 공지로 교체 =====
  const renderCurrentNotice = () => {
    if (isCreatingMeetup) {
      return <ChatNotice key="meetup-loading" text="모임을 만들고 있어요." />
    }

    if (meetupCreationError) {
      return (
        <ChatNotice
          key={`meetup-error-${meetupCreationError}`}
          text={meetupCreationError}
        />
      )
    }

    if (chatConnectionError) {
      return <ChatNotice key={`chat-error-${chatConnectionError}`} text={chatConnectionError} />
    }

    if (voteSession && canViewVote) {
      return (
        <ChatNotice
          key={`vote-${voteSession.id ?? voteSession.roundId}-${voteSession.status}`}
          text={getVoteNoticeText(voteSession.status)}
        >
          <MomeokjiVoteNotice
            status={voteSession.status}
            onOpenVote={() => setIsVotePageOpen(true)}
          />
        </ChatNotice>
      )
    }

    if (recommendationError) {
      return (
        <ChatNotice
          key={`recommendation-error-${recommendationError}`}
          text={recommendationError}
        />
      )
    }

    if (preferenceSession && canViewPreference) {
      return (
        <ChatNotice
          key={`preference-${preferenceSession.meetupId}-${preferenceSession.status}`}
          text={getPreferenceNoticeText(preferenceSession.status)}
        >
          <MomeokjiPreferenceNotice
            status={preferenceSession.status}
            participantCount={preferenceSession.participantIds.length}
            submittedCount={preferenceSession.submittedParticipantIds.length}
            deadlineAt={preferenceSession.deadlineAt}
            hasSubmitted={includesMemberId(
              preferenceSession.submittedParticipantIds,
              currentUser.id,
            )}
            onOpen={() => setIsParticipantPreferenceOpen(true)}
          />
        </ChatNotice>
      )
    }

    if (isCreatingVote) {
      return <ChatNotice key="recommendation-loading" text="AI가 추천 식당을 찾고 있어요." />
    }

    return null
  }

  // ===== 새 기능 시작 시각을 만들고 공통 설정·키워드 분석 상태를 새 세션으로 초기화 =====
  const startMomeokjiSession = () => {
    setMomeokjiFeatureStartedAt(new Date().toISOString())
    setIsMomeokjiOpen(true)
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
    startMomeokjiSession()
  }

  // ===== 기존 결과는 보존한 채 새 모먹지 공통 설정을 1단계부터 시작 =====
  const restartMomeokji = () => {
    pendingMeetingSettingsRef.current = null
    voteSubmissionLockRef.current = false
    setPendingMeetingSettings(null)
    setPreferenceSession(null)
    setMeetupCreationError('')
    setRecommendationError('')
    setIsParticipantPreferenceOpen(false)
    setIsVotePageOpen(false)
    startMomeokjiSession()
  }

  // ===== 새 설정을 취소하면 보존한 이전 투표 결과 화면으로 복귀 =====
  const closeMomeokjiSetup = (reason = 'cancel') => {
    setIsMomeokjiOpen(false)
    if (reason === 'cancel' && voteSession?.status === 'CLOSED') {
      setIsVotePageOpen(true)
    }
  }

  // ===== 한 사람이 선택한 최대 4개 표를 서버의 현재 회차에 저장 =====
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
    if (voteSession.source !== 'server') {
      setRecommendationError('서버에서 생성된 투표 회차만 제출할 수 있습니다.')
      return
    }

    const candidateIds = uniqueOptionIds.map((optionId) => (
      optionId === RECOMMEND_AGAIN_ID
        ? voteSession.recommendAgainCandidateId
        : Number(optionId)
    ))
    if (candidateIds.some((candidateId) => !Number.isFinite(candidateId))) {
      setRecommendationError('서버의 재투표 후보 정보를 찾지 못했습니다.')
      return
    }

    voteSubmissionLockRef.current = true
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
      voteSubmissionLockRef.current = false
    }
  }

  return (
    <div className="chat-room">
      {/* 필요 없는 영역은 이 조립부에서 컴포넌트 한 줄만 제거. */}
      {/* ===== 채팅방 이름과 참가자 수: 실제 채팅방 조회 API 데이터와 연동 ===== */}
      <ChatHeader
        roomName={roomInfo?.name ?? '채팅방'}
        memberCount={roomParticipants.length}
        joinCode={roomInfo?.joinCode}
        onBack={() => navigate('/chats')}
      />

      <div className="chat-body">
        {/* ===== 새 상태가 생기면 이전 내용을 교체하는 단일 공지 영역 ===== */}
        {renderCurrentNotice()}
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
        key={momeokjiFeatureStartedAt ?? 'momeokji-idle'}
        open={isMomeokjiOpen}
        onClose={closeMomeokjiSetup}
        onComplete={requestParticipantPreference}
        chatRoomId={room.id}
        featureStartedAt={momeokjiFeatureStartedAt}
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
        onRestart={restartMomeokji}
        onSubmit={submitVote}
        isResolvingVote={isResolvingVote}
        result={canViewMomeokjiResult ? momeokjiResult : null}
      />
    </div>
  )
}

export default ChatRoomPage
