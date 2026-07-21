export const TOTAL_STEPS = 8

// 화면과 API 모두 24시간제(HH:mm)를 사용해 오전/오후 변환 오류를 방지합니다.
export const TIME_OPTIONS = Array.from({ length: 24 }, (_, hour) => {
  const value = `${String(hour).padStart(2, '0')}:00`
  return { value, label: value }
})

// label은 화면 표시용, value는 AI·백엔드 API 전달용 고정 코드입니다.
export const THEMES = [
  { value: 'MEAL', label: '식사' },
  { value: 'CAFE', label: '카페' },
  { value: 'MEETING', label: '미팅' },
  { value: 'DRINK', label: '술자리' },
  { value: 'DESSERT', label: '디저트' },
  { value: 'DATE', label: '데이트' },
  { value: 'CASUAL', label: '가벼운 모임' },
  { value: 'UNDECIDED', label: '미정' },
]

export const AVOID_OPTIONS = ['매운 음식', '날것', '밀가루', '기름진 음식', '없어요']

export const MOOD_OPTIONS = [
  '든든한',
  '가벼운',
  '조용한',
  '활기찬',
  '새로운 메뉴',
  '상관없어요',
]

export const CATEGORY_OPTIONS = ['한식', '중식', '일식', '양식', '분식', '카페/디저트', '고기', '아시안']

// 목적지 좌표 기준 도보 이동 가능 시간(분). AI 추천의 walkMinutes 조건으로 그대로 전달됩니다.
export const WALK_MINUTES_OPTIONS = [5, 10, 15, 20, 30]
