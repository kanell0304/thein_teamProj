// ===== 참가자가 현재 라운드에 선택 항목들을 이미 제출했는지 확인 =====
export function hasParticipantVoted(votes, participantId) {
  return Object.values(votes).some((voterIds) => voterIds.includes(participantId))
}

// ===== 추천 가게 3곳의 득표수를 백엔드 전송용 객체로 변환 =====
export function createVoteCounts(recommendations, votes) {
  return Object.fromEntries(
    recommendations.map((restaurant) => [restaurant.id, votes[restaurant.id]?.length ?? 0]),
  )
}

// ===== 선택된 참가자가 모두 한 번씩 투표를 제출했는지 확인 =====
export function hasEveryoneVoted(participantIds, votes) {
  const votedParticipantIds = new Set(Object.values(votes).flat())
  return participantIds.every((participantId) => votedParticipantIds.has(participantId))
}

// ===== 재투표가 가게 후보의 최고 득표수보다 많을 때만 단독 1위로 판정 =====
export function hasRecommendAgainWon(recommendations, votes) {
  const recommendAgainCount = votes[RECOMMEND_AGAIN_ID]?.length ?? 0
  const highestRestaurantCount = Math.max(
    ...recommendations.map((restaurant) => votes[restaurant.id]?.length ?? 0),
  )

  return recommendAgainCount > highestRestaurantCount
}

// ===== 최고 득표 가게를 결정하고 가게끼리 동률이면 그 가게들 중 무작위 선택 =====
export function findWinningRestaurant(recommendations, votes, randomValue = Math.random()) {
  const highestCount = Math.max(
    ...recommendations.map((restaurant) => votes[restaurant.id]?.length ?? 0),
  )
  const leaders = recommendations.filter((restaurant) => (
    (votes[restaurant.id]?.length ?? 0) === highestCount
  ))

  return pickRandomRestaurant(leaders, randomValue)
}

// ===== 전달받은 가게 후보 중 한 곳을 편향 없이 무작위 선택 =====
export function pickRandomRestaurant(recommendations, randomValue = Math.random()) {
  if (recommendations.length === 0) return null

  const safeRandomValue = Math.min(Math.max(randomValue, 0), 0.999999999)
  return recommendations[Math.floor(safeRandomValue * recommendations.length)]
}
// 재투표 선택지는 실제 가게 ID와 충돌하지 않는 고정값으로 관리합니다.
export const RECOMMEND_AGAIN_ID = '__recommend_again__'
