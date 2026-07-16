import { attachRestaurantImages } from './placeImageApi'

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

// ===== 대화·모임 조건을 AI 추천 API로 전달 =====
export async function analyzeConversationMenus(
  messages,
  { themeCode, meetingDate, meetingTime, timeZone, place } = {},
) {
  // 기능 버블을 제외하고 실제 텍스트 대화만 AI 분석 데이터로 전달합니다.
  const textMessages = messages.filter((message) => typeof message.text === 'string')
  const endpoint = import.meta.env.VITE_MOMEOKJI_AI_URL
  if (endpoint) {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
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
      }),
    })
    if (!response.ok) throw new Error('메뉴 분석에 실패했습니다.')
    const data = await response.json()
    return data.menus
  }

  const conversation = textMessages.map((message) => message.text).join(' ')
  const found = FALLBACK_MENUS.filter((menu) => conversation.includes(menu))
  return found.length ? found : FALLBACK_MENUS
}

// ===== 모든 모먹지 조건을 AI 가게 추천 API로 전달 =====
export async function recommendRestaurants(
  criteria,
  { excludeRestaurantIds = [], generation = 0 } = {},
) {
  const endpoint = import.meta.env.VITE_MOMEOKJI_RECOMMEND_URL
  let recommendations

  if (endpoint) {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...criteria,
        excludeRestaurantIds,
        recommendationCount: 3,
        generation,
      }),
    })

    if (!response.ok) throw new Error('추천 가게를 불러오지 못했습니다.')
    const data = await response.json()
    const uniqueRestaurantIds = new Set()

    // ===== 백엔드 응답도 누적 제외 목록과 대조하여 중복 가게 노출 방지 =====
    recommendations = data.recommendations.filter((restaurant) => {
      const isExcluded = excludeRestaurantIds.includes(restaurant.id)
      const isDuplicated = uniqueRestaurantIds.has(restaurant.id)
      uniqueRestaurantIds.add(restaurant.id)
      return !isExcluded && !isDuplicated
    }).slice(0, 3)

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

  // AI 추천 결과를 위치정보 API에 넘겨 동일한 가게의 이미지를 합칩니다.
  return attachRestaurantImages(recommendations, criteria.place)
}
