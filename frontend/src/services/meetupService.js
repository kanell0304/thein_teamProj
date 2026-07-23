import { fetchMeetup, postMeetup } from '../api/meetupApi'

export const USE_MOCK_API = String(
  import.meta.env.VITE_USE_MOCK ?? 'false',
).toLowerCase() === 'true'

const mockMeetups = new Map()

// ===== 숫자 ID와 좌표가 백엔드 DTO 조건을 만족하는지 확인 =====
function requirePositiveNumber(value, fieldName) {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue) || numberValue <= 0) {
    throw new TypeError(`${fieldName} 값이 올바르지 않습니다.`)
  }
  return numberValue
}

// ===== 화면의 날짜·24시간 값을 백엔드 LocalDateTime 형식으로 변환 =====
function createMeetingDateTime(date, time) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date || '')) {
    throw new TypeError('약속 날짜 값이 올바르지 않습니다.')
  }
  if (!/^\d{2}:\d{2}(:\d{2})?$/.test(time || '')) {
    throw new TypeError('약속 시간 값이 올바르지 않습니다.')
  }
  return `${date}T${time.length === 5 ? `${time}:00` : time}`
}

// ===== Date 또는 ISO 문자열을 시간대 표기가 없는 LocalDateTime 문자열로 변환 =====
function normalizeDeadline(value) {
  if (!value) return null
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) {
    return value.length === 16 ? `${value}:00` : value
  }

  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) throw new TypeError('투표 마감 시각이 올바르지 않습니다.')

  const parts = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
    String(date.getHours()).padStart(2, '0'),
    String(date.getMinutes()).padStart(2, '0'),
    String(date.getSeconds()).padStart(2, '0'),
  ]
  return `${parts[0]}-${parts[1]}-${parts[2]}T${parts[3]}:${parts[4]}:${parts[5]}`
}

// ===== 모먹지 설정 화면 값을 POST /api/meetups 요청 DTO로 매핑 =====
export function createMeetupRequest({
  chatRoomId,
  settings,
  personalOptionDeadlineAt,
  voteDeadlineAt = null,
}) {
  const place = settings?.place
  if (!place?.name) throw new TypeError('선택한 장소 이름이 필요합니다.')

  return {
    chatRoomId: requirePositiveNumber(chatRoomId, '채팅방 ID'),
    participantIds: (settings.participantIds ?? []).map((participantId) => (
      requirePositiveNumber(participantId, '참가자 ID')
    )),
    commonOption: {
      destinationName: place.name,
      destinationLatitude: requirePositiveNumber(place.latitude, '장소 위도'),
      destinationLongitude: requirePositiveNumber(place.longitude, '장소 경도'),
      meetingTime: createMeetingDateTime(settings.date, settings.time),
      purpose: settings.themeLabel || settings.themeCode,
    },
    personalOptionDeadlineAt: normalizeDeadline(personalOptionDeadlineAt),
    voteDeadlineAt: normalizeDeadline(voteDeadlineAt),
    voteDurationMinutes: Number(settings.voteDurationMinutes ?? 10),
  }
}

// ===== 백엔드 없이도 이후 화면 연결을 시험할 수 있는 모임 생성 응답 =====
function createMockMeetup(request) {
  const id = `mock-meetup-${crypto.randomUUID()}`
  const meetup = {
    id,
    meetupId: id,
    chatRoomId: request.chatRoomId,
    status: 'RECOMMENDING',
    commonOption: request.commonOption,
    personalOptionDeadlineAt: request.personalOptionDeadlineAt,
    latestRound: null,
    voteDeadlineAt: request.voteDeadlineAt,
    voteDurationMinutes: request.voteDurationMinutes,
  }
  mockMeetups.set(id, meetup)
  return meetup
}

// ===== 화면 입력값을 변환한 뒤 모임 생성 API를 호출 =====
export async function createMeetup(input, { signal } = {}) {
  const request = createMeetupRequest(input)
  if (USE_MOCK_API) return createMockMeetup(request)

  try {
    return await postMeetup(request, { signal })
  } catch (error) {
    throw new Error(error.userMessage || '모임을 만들지 못했습니다.', { cause: error })
  }
}

// ===== 재접속 시 현재 추천 회차와 투표 상태를 복구 =====
export async function getMeetup(meetupId, { signal } = {}) {
  if (USE_MOCK_API) {
    const meetup = mockMeetups.get(String(meetupId))
    if (!meetup) throw new Error('목업 모임 정보를 찾을 수 없습니다.')
    return meetup
  }

  try {
    return await fetchMeetup(meetupId, { signal })
  } catch (error) {
    throw new Error(error.userMessage || '모임 정보를 불러오지 못했습니다.', { cause: error })
  }
}
