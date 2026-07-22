import {
  postConversationMenuAnalysis,
  postRestaurantRecommendation,
} from '../api/momeokjiApi'
import { attachRestaurantImages } from './placeImageService'

const FALLBACK_MENUS = ['돈까스', '파스타', '초밥', '치킨', '햄버거', '떡볶이']

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

// ===== 대화·모임 조건을 AI 메뉴 분석 요청 형태로 가공 =====
export async function analyzeConversationMenus(
  messages,
  { themeCode, meetingDate, meetingTime, timeZone, place } = {},
) {
  const textMessages = messages.filter((message) => typeof message.text === 'string')
  const endpoint = import.meta.env.VITE_MOMEOKJI_AI_URL

  if (endpoint) {
    try {
      const data = await postConversationMenuAnalysis(endpoint, {
        themeCode,
        meetingDate,
        meetingTime,
        timeZone,
        place: place ? {
          placeId: place.id,
          provider: place.provider,
          name: place.name,
          address: place.address,
          latitude: place.latitude,
          longitude: place.longitude,
        } : null,
        messages: textMessages.map(({ name, text }) => ({ name, text })),
      })
      return data.menus
    } catch (error) {
      throw new Error(error.userMessage || '메뉴 분석에 실패했습니다.', { cause: error })
    }
  }

  const conversation = textMessages.map((message) => message.text).join(' ')
  const found = FALLBACK_MENUS.filter((menu) => conversation.includes(menu))
  return found.length ? found : FALLBACK_MENUS
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
