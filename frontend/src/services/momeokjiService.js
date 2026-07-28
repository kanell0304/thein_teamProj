import { getChatRoomMenuKeywords } from './chatApi'

// ===== 기능 시작 직전 2시간의 채팅에서 백엔드가 추출한 유형별 키워드 점수를 조회 =====
export async function analyzeConversationKeywords(chatRoomId, featureStartedAt, participantIds) {
  try {
    const data = await getChatRoomMenuKeywords(chatRoomId, featureStartedAt, participantIds)
    return Array.isArray(data?.keywordScores) ? data.keywordScores : []
  } catch (error) {
    throw new Error(error.userMessage || '대화 추천 항목을 불러오지 못했습니다.', {
      cause: error,
    })
  }
}
