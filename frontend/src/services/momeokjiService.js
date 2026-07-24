import { postRestaurantRecommendation } from '../api/momeokjiApi'
import { getChatRoomMenuKeywords } from './chatApi'
import { attachRestaurantImages } from './placeImageService'

// ===== 추천 API 미연결 상태에서 사용하는 3개 단위 가게 묶음 =====
const FALLBACK_RECOMMENDATION_GROUPS = [
  [
    { id: 'orange-table', name: '오렌지 테이블', menuName: '오늘의 한식', priceRange: '15,000원대', visual: '🍲' },
    { id: 'moon-pasta', name: '문정 파스타룸', menuName: '생면 파스타', priceRange: '20,000원대', visual: '🍝' },
    { id: 'sushi-haru', name: '스시 하루', menuName: '모둠 초밥', priceRange: '25,000원대', visual: '🍣' },
  ],
  [
    { id: 'table-101', name: '테이블 101', menuName: '그릴 플레이트', priceRange: '25,000원대', visual: '🥩' },
    { id: 'seoul-kitchen', name: '서울 키친', menuName: '한상 차림', priceRange: '18,000원대', visual: '🍚' },
    { id: 'night-noodle', name: '심야 면식당', menuName: '온면과 만두', priceRange: '12,000원대', visual: '🍜' },
  ],
]

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

// ===== API 추천 결과에서 제외·중복 식당을 제거하고 3곳만 반환 =====
function filterUniqueRecommendations(recommendations, excludeRestaurantIds) {
  const uniqueRestaurantIds = new Set()

  return recommendations.filter((restaurant) => {
    const isExcluded = excludeRestaurantIds.includes(restaurant.id)
    const isDuplicated = uniqueRestaurantIds.has(restaurant.id)
    uniqueRestaurantIds.add(restaurant.id)
    return !isExcluded && !isDuplicated
  }).slice(0, 3)
}

// ===== 모든 모먹지 조건을 AI 가게 추천 요청 형태로 가공 =====
export async function recommendRestaurants(
  criteria,
  { excludeRestaurantIds = [], generation = 0 } = {},
) {
  const endpoint = import.meta.env.VITE_MOMEOKJI_RECOMMEND_URL
  let recommendations

  if (endpoint) {
    try {
      const data = await postRestaurantRecommendation(endpoint, {
        ...criteria,
        excludeRestaurantIds,
        recommendationCount: 3,
        generation,
      })
      recommendations = filterUniqueRecommendations(
        data.recommendations,
        excludeRestaurantIds,
      )
    } catch (error) {
      throw new Error(error.userMessage || '추천 가게를 불러오지 못했습니다.', { cause: error })
    }

    if (recommendations.length !== 3) {
      throw new Error('중복되지 않는 추천 가게 3곳이 필요합니다.')
    }
  } else {
    const fallbackGroup = FALLBACK_RECOMMENDATION_GROUPS[
      generation % FALLBACK_RECOMMENDATION_GROUPS.length
    ]

    recommendations = fallbackGroup.map((restaurant) => ({
      ...restaurant,
      id: `${restaurant.id}-${generation}`,
      address: criteria.place?.address ?? '',
      distanceLabel: criteria.place?.name ? `${criteria.place.name} 주변` : '선택 장소 주변',
      imageUrl: '',
    }))
  }

  return attachRestaurantImages(recommendations, criteria.place)
}
