// 모임 생성은 공통 설정 6개와 AI 대화 메뉴 확인 1개로 구성합니다.
export const TOTAL_STEPS = 7

// 제한시간은 화면 검증과 백엔드 DTO에서 같은 범위를 사용합니다.
export const PERSONAL_OPTION_DURATION = { min: 3, defaultValue: 10, max: 30 }
export const VOTE_DURATION = { min: 3, defaultValue: 10, max: 60 }

// 특정 메뉴 선호가 없을 때도 다른 메뉴와 함께 선택 가능한 추천 조건입니다.
export const MENU_ANY_OPTION = '아무거나'

// 화면과 API 모두 24시간제(HH:mm)를 사용해 오전/오후 변환 오류를 방지합니다.
export const TIME_OPTIONS = Array.from({ length: 24 }, (_, hour) => {
  const value = `${String(hour).padStart(2, '0')}:00`
  return { value, label: value }
})

// label은 화면 표시용, value는 AI·백엔드 API 전달용 고정 코드입니다.
export const THEMES = [
  { value: 'MEAL', label: '식사' },
  { value: 'MEETING', label: '미팅' },
  { value: 'GATHERING', label: '모임' },
  { value: 'CAFE', label: '카페' },
  { value: 'DESSERT', label: '디저트' },
  { value: 'DATE', label: '데이트' },
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
