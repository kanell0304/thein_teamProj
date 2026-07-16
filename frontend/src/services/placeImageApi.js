// ===== 위치정보 API 응답을 AI 추천 가게와 ID 또는 이름으로 매칭 =====
function findMatchedPlace(restaurant, places) {
  return places.find((place) => (
    (place.restaurantId && place.restaurantId === restaurant.id)
    || (place.placeId && restaurant.placeId && place.placeId === restaurant.placeId)
    || place.name === restaurant.name
  ))
}

/**
 * AI 추천 가게 3곳을 위치정보 API에 전달하고 이미지·위치 식별자를 합칩니다.
 * API가 아직 없거나 실패하면 기존 추천 정보로 화면을 계속 표시합니다.
 */
export async function attachRestaurantImages(restaurants, meetingPlace) {
  const endpoint = import.meta.env.VITE_PLACE_IMAGE_LOOKUP_URL
  if (!endpoint) return restaurants

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        meetingPlace,
        restaurants: restaurants.map((restaurant) => ({
          restaurantId: restaurant.id,
          placeId: restaurant.placeId ?? null,
          name: restaurant.name,
          address: restaurant.address ?? '',
          latitude: restaurant.latitude ?? null,
          longitude: restaurant.longitude ?? null,
        })),
      }),
    })

    if (!response.ok) return restaurants
    const data = await response.json()
    const places = data.places ?? data.restaurants ?? []

    return restaurants.map((restaurant) => {
      const matchedPlace = findMatchedPlace(restaurant, places)
      if (!matchedPlace) return restaurant

      return {
        ...restaurant,
        placeId: matchedPlace.placeId ?? restaurant.placeId,
        imageUrl: matchedPlace.imageUrl ?? matchedPlace.thumbnailUrl ?? restaurant.imageUrl ?? '',
        address: matchedPlace.address ?? restaurant.address,
        latitude: matchedPlace.latitude ?? restaurant.latitude,
        longitude: matchedPlace.longitude ?? restaurant.longitude,
      }
    })
  } catch {
    return restaurants
  }
}
