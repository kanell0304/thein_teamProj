import { useEffect, useRef, useState } from 'react'
import ChatHeader from '../components/chat/ChatHeader'
import ChatNotice from '../components/chat/ChatNotice'
import ChatMessageList from '../components/chat/ChatMessageList'
import ChatInput from '../components/chat/ChatInput'
import MomeokjiPage from './MomeokjiPage'
import PersonalPreferencePage from './PersonalPreferencePage'
import MomeokjiVoteNotice from '../components/momeokji/MomeokjiVoteNotice'
import MomeokjiVotePage from '../components/momeokji/MomeokjiVotePage'
import MomeokjiResult from '../components/momeokji/MomeokjiResult'
import { ensureDevSession } from '../services/authApi'
import { ensureTestChatRoom, getChatRoomMembers, getRecentMessages } from '../services/chatApi'
import {
  castVote,
  createMeetup,
  forceStartRecommendation,
  getActiveMeetup,
  getMeetupParticipants,
  submitMyPreference,
} from '../services/meetupApi'
import { connectChatSocket, disconnectChatSocket, sendChatMessage } from '../services/chatSocket'
import './ChatRoomPage.css'

// ===== 채팅 서버 응답(ChatMessageResponse)을 화면이 쓰는 메시지 형태로 변환 =====
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

function ChatRoomPage() {
  const [messages, setMessages] = useState([])
  const [isMomeokjiOpen, setIsMomeokjiOpen] = useState(false)
  const [isVotePageOpen, setIsVotePageOpen] = useState(false)

  // ===== 실 채팅 연동: dev 세션으로 방 이력을 불러오고 실시간 소켓을 구독 =====
  const [chatRoomId, setChatRoomId] = useState(null)
  const [chatMembers, setChatMembers] = useState([])
  const [currentUser, setCurrentUser] = useState(null)
  const [isChatConnecting, setIsChatConnecting] = useState(true)
  const [chatError, setChatError] = useState('')
  const chatSocketRef = useRef(null)
  const chatMemberIdRef = useRef(null)
  // ParticipantPicker의 기존 {id, name} 계약을 그대로 재사용합니다.
  const roomParticipants = chatMembers.map((member) => ({ id: member.id, name: member.nickname }))

  // ===== 모임 초대: 백엔드 모임 생성 즉시 발행되는 초대 이벤트로 갱신됩니다 =====
  const [meetup, setMeetup] = useState(null)
  const [participants, setParticipants] = useState([])
  const [isCreatingMeetup, setIsCreatingMeetup] = useState(false)
  const [meetupError, setMeetupError] = useState('')
  const myParticipant = participants.find((participant) => participant.memberId === currentUser?.id)
  const hasSubmittedPreference = myParticipant?.submissionStatus === 'SUBMITTED'
  const isHost = Boolean(currentUser) && meetup?.hostMemberId === currentUser.id
  const pendingParticipantCount = participants.filter((participant) => participant.submissionStatus === 'PENDING').length

  // ===== 개인 선호 제출 + AI 추천 실행 상태: 전원 제출 완료 시 서버가 자동으로 추천을 실행합니다 =====
  const [isPersonalPreferenceOpen, setIsPersonalPreferenceOpen] = useState(false)
  const [isSubmittingPreference, setIsSubmittingPreference] = useState(false)
  const [isForcingStart, setIsForcingStart] = useState(false)
  const [preferenceError, setPreferenceError] = useState('')
  const [recommendationStatus, setRecommendationStatus] = useState('IDLE')
  const [round, setRound] = useState(null)

  // ===== 투표: 후보별 실시간 득표는 vote-updates 웹소켓이 round를 갱신합니다 =====
  const [isSubmittingVote, setIsSubmittingVote] = useState(false)
  const [voteError, setVoteError] = useState('')

  // ===== 최종 확정: 전원 투표 완료 시 서버가 자동으로 확정하고 채팅방 상단에 고정 공지로 알려줍니다 =====
  const [finalNotice, setFinalNotice] = useState(null)

  useEffect(() => {
    let cancelled = false

    async function bootstrapChat() {
      try {
        const session = await ensureDevSession()
        const roomId = await ensureTestChatRoom()
        const [history, members] = await Promise.all([
          getRecentMessages(roomId),
          getChatRoomMembers(roomId),
        ])
        if (cancelled) return

        chatMemberIdRef.current = session.memberId
        const socket = await connectChatSocket(roomId, {
          onMessage: (incoming) => {
            setMessages((previous) => [...previous, toUiMessage(incoming, chatMemberIdRef.current)])
          },
          onInvitation: (event) => {
            setMeetup(event.meetup)
            setParticipants(event.participants)
          },
          onProgress: (event) => {
            setParticipants(event.participants)
          },
          onRecommendationProgress: (event) => {
            setRecommendationStatus(event.status)
            if (event.status === 'COMPLETED') setRound(event.result)
            if (event.status === 'FAILED') setPreferenceError(event.errorMessage || 'AI 추천에 실패했어요.')
          },
          onVoteUpdate: (event) => {
            setRound(event)
          },
          onFinalNotice: (event) => {
            setFinalNotice(event)
          },
        })
        if (cancelled) {
          disconnectChatSocket(socket)
          return
        }

        chatSocketRef.current = socket
        setChatRoomId(roomId)
        setChatMembers(members)
        setCurrentUser({ id: session.memberId, name: session.nickname })
        setMessages(history.map((message) => toUiMessage(message, session.memberId)))

        // ===== 새로고침/재접속 시 놓친 웹소켓 이벤트를 한 번의 조회로 복원합니다 =====
        const activeMeetup = await getActiveMeetup(roomId)
        if (cancelled || !activeMeetup) return

        setMeetup(activeMeetup)
        setParticipants(await getMeetupParticipants(activeMeetup.meetupId))
        if (activeMeetup.latestRound) {
          setRound(activeMeetup.latestRound)
          setRecommendationStatus('COMPLETED')
        }
        if (activeMeetup.finalNotice) setFinalNotice(activeMeetup.finalNotice)
      } catch {
        if (!cancelled) setChatError('채팅 서버에 연결하지 못했어요. 백엔드가 켜져 있는지 확인해주세요.')
      } finally {
        if (!cancelled) setIsChatConnecting(false)
      }
    }

    bootstrapChat()

    return () => {
      cancelled = true
      disconnectChatSocket(chatSocketRef.current)
    }
  }, [])

  const sendMessage = (text) => {
    if (!chatRoomId || !chatSocketRef.current) return
    sendChatMessage(chatSocketRef.current, chatRoomId, text)
  }

  // ===== 공통 설정 완료 후 모임을 만들고 선택한 참가자를 초대합니다 =====
  const createMeetupAndInvite = async (settings) => {
    if (!chatRoomId) return
    setIsCreatingMeetup(true)
    setMeetupError('')

    try {
      await createMeetup({
        chatRoomId,
        commonOption: {
          destinationName: settings.place.name,
          destinationLatitude: settings.place.latitude,
          destinationLongitude: settings.place.longitude,
          meetingTime: `${settings.date}T${settings.time}:00`,
          purpose: settings.themeLabel,
        },
        participantIds: settings.participantIds,
      })
      // 생성 즉시 서버가 초대 이벤트를 브로드캐스트하므로, meetup/participants는 onInvitation 핸들러가 채웁니다.
    } catch {
      setMeetupError('모임을 만들지 못했어요. 다시 시도해주세요.')
    } finally {
      setIsCreatingMeetup(false)
    }
  }

  // ===== 초대받은 참가자가 개인 선호를 제출합니다. 전원 제출 완료 시 서버가 자동으로 AI 추천을 실행합니다. =====
  const submitPreference = async (preference) => {
    if (!meetup) return
    setIsSubmittingPreference(true)
    setPreferenceError('')

    try {
      await submitMyPreference(meetup.meetupId, preference)
      setIsPersonalPreferenceOpen(false)
      // participants/round 갱신은 meetup-progress·recommendation-progress 웹소켓 이벤트가 담당합니다.
    } catch {
      setPreferenceError('선호를 제출하지 못했어요. 다시 시도해주세요.')
    } finally {
      setIsSubmittingPreference(false)
    }
  }

  // ===== 호스트가 일부만 제출한 상태에서 강제로 AI 추천을 진행하거나, 완료된 추천을 다시 받습니다. =====
  const forceStart = async () => {
    if (!meetup) return
    setIsForcingStart(true)
    setPreferenceError('')

    try {
      await forceStartRecommendation(meetup.meetupId)
    } catch {
      setPreferenceError('지금까지 제출된 선호로 진행하지 못했어요.')
    } finally {
      setIsForcingStart(false)
    }
  }

  // ===== 선택한 후보마다 실제 투표 API를 호출합니다(다중 선택이라 후보별로 각각 호출). =====
  const castMyVote = async (selectedOptionIds) => {
    if (!meetup || !round) return
    setIsSubmittingVote(true)
    setVoteError('')

    try {
      await Promise.all(
        selectedOptionIds.map((candidateId) => castVote(meetup.meetupId, round.roundId, Number(candidateId))),
      )
      // 득표 갱신은 vote-updates 웹소켓이 round를 다시 채워줍니다.
    } catch {
      setVoteError('투표를 반영하지 못했어요. 다시 시도해주세요.')
    } finally {
      setIsSubmittingVote(false)
    }
  }

  // ===== 모먹지 기능 버튼은 진행 상태에 맞는 화면을 엽니다. =====
  const openCurrentMomeokjiStage = async () => {
    if (recommendationStatus === 'COMPLETED' && round) {
      setIsVotePageOpen(true)
      return
    }
    if (meetup && myParticipant && !hasSubmittedPreference) {
      setIsPersonalPreferenceOpen(true)
      return
    }
    // 처음 연결한 뒤 새로 들어온 멤버가 있을 수 있으니, 참가자를 고르기 직전에 목록을 다시 불러옵니다.
    if (chatRoomId) {
      try {
        setChatMembers(await getChatRoomMembers(chatRoomId))
      } catch {
        // 갱신 실패해도 기존 목록으로 마법사는 계속 열어줍니다.
      }
    }
    setIsMomeokjiOpen(true)
  }

  return (
    <div className="chat-room">
      {/* 필요 없는 영역은 이 조립부에서 컴포넌트 한 줄만 제거. */}
      <ChatHeader roomName="진원버스 가즈아" memberCount={chatMembers.length} />

      <div className="chat-body">
        {/* ===== 최종 확정 고정 공지: 전원 투표 완료 시 서버가 자동으로 확정하며, 다른 공지보다 항상 위에 표시합니다 ===== */}
        {finalNotice && (
          <ChatNotice text={`모먹지 결과가 나왔어요 · ${finalNotice.restaurantName}`} defaultExpanded>
            <MomeokjiResult finalNotice={finalNotice} meetup={meetup} />
          </ChatNotice>
        )}

        {/* ===== 실 채팅 연동 상태: dev 세션 발급 + 방 입장 + 소켓 연결까지의 진행 상황 ===== */}
        {isChatConnecting && <ChatNotice text="채팅 서버에 연결하고 있어요." />}
        {chatError && <ChatNotice text={chatError} />}

        {/* ===== 공통 설정 완료 후 모임 생성 상태 ===== */}
        {isCreatingMeetup && <ChatNotice text="모임을 만들고 있어요." />}
        {meetupError && <ChatNotice text={meetupError} />}

        {preferenceError && <ChatNotice text={preferenceError} />}
        {voteError && <ChatNotice text={voteError} />}

        {/* ===== 초대받은 참가자에게만 보이는 모임 공지: 전원 제출 완료 시 서버가 자동으로 AI 추천을 실행합니다 ===== */}
        {!finalNotice && meetup && myParticipant && recommendationStatus !== 'COMPLETED' && (
          <ChatNotice
            text={
              recommendationStatus === 'STARTED'
                ? 'AI가 추천 가게를 찾고 있어요.'
                : hasSubmittedPreference
                  ? `제출 완료! 다른 참가자 ${pendingParticipantCount}명을 기다리고 있어요.`
                  : '모먹지가 시작됐어요. 개인 선호를 입력해주세요.'
            }
          >
            <MomeokjiVoteNotice
              status="CREATED"
              onOpenVote={
                hasSubmittedPreference || recommendationStatus === 'STARTED'
                  ? undefined
                  : () => setIsPersonalPreferenceOpen(true)
              }
            />
            {isHost && pendingParticipantCount > 0 && recommendationStatus !== 'STARTED' && (
              <button
                className="app-button app-button--small"
                type="button"
                disabled={isForcingStart}
                onClick={forceStart}
              >
                {isForcingStart ? '진행하는 중...' : '지금까지 제출된 선호로 진행하기'}
              </button>
            )}
          </ChatNotice>
        )}

        {/* ===== AI 추천 완료: 참가자는 투표하러 갈 수 있고 호스트는 다시 추천받을 수 있습니다 ===== */}
        {!finalNotice && meetup && myParticipant && recommendationStatus === 'COMPLETED' && round && (
          <ChatNotice text={`추천이 완료됐어요. 후보 ${round.candidates.length}곳 중에서 투표해주세요.`}>
            <MomeokjiVoteNotice status="CREATED" onOpenVote={() => setIsVotePageOpen(true)} />
            {isHost && (
              <button
                className="app-button app-button--small"
                type="button"
                disabled={isForcingStart}
                onClick={forceStart}
              >
                {isForcingStart ? '다시 추천받는 중...' : '다시 추천받기'}
              </button>
            )}
          </ChatNotice>
        )}

        <ChatMessageList
          messages={messages}
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
        onComplete={createMeetupAndInvite}
        messages={messages}
        participants={roomParticipants}
        defaultParticipantIds={currentUser ? [currentUser.id] : []}
      />

      <PersonalPreferencePage
        open={isPersonalPreferenceOpen}
        onClose={() => setIsPersonalPreferenceOpen(false)}
        onSubmit={submitPreference}
        isSubmitting={isSubmittingPreference}
      />

      <MomeokjiVotePage
        key={round ? `${round.meetupId}-${round.roundId}` : 'no-round'}
        open={isVotePageOpen}
        round={round}
        onClose={() => setIsVotePageOpen(false)}
        onSubmit={castMyVote}
        isSubmitting={isSubmittingVote}
      />
    </div>
  )
}

export default ChatRoomPage
