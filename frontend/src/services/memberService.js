import { fetchMembers } from '../api/memberApi'

const USE_MOCK_API = String(import.meta.env.VITE_USE_MOCK ?? 'false').toLowerCase() === 'true'

// ===== 백엔드 연결 전에도 친구 목록 화면을 확인할 수 있는 목업 =====
const MOCK_MEMBERS = [
  { id: 'member-seojun', nickname: '서준', statusMessage: '점심 메뉴 추천 환영' },
  { id: 'member-gyeongjun', nickname: '경준', statusMessage: '오늘도 든든하게' },
]

// ===== 실행 환경에 따라 목업 또는 실제 회원 API를 사용 =====
export async function getMembers({ signal } = {}) {
  if (USE_MOCK_API) return MOCK_MEMBERS

  try {
    return await fetchMembers({ signal })
  } catch (error) {
    throw new Error(error.userMessage || '회원 목록을 불러오지 못했습니다.', { cause: error })
  }
}
