import { postPlaceImageLookup } from '../api/placeImageApi'

// ===== 위치정보 API 응답을 AI 추천 가게와 ID 또는 이름으로 매칭 =====
function findMatchedPlace(restaurant, places) {
  return places.find((place) => (
    (place.restaurantId && place.restaurantId === restaurant.id)
    || (place.placeId && restaurant.placeId && place.placeId === restaurant.placeId)
    || place.name === restaurant.name
  ))
}

// ===== 위치정보 API 응답의 이미지·좌표를 추천 식당 데이터에 결합 =====
function mergePlaceImages(restaurants, places) {
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
}

// ===== 추천 가게를 위치정보 API에 전달하고 실패하면 기존 데이터를 유지 =====
export async function attachRestaurantImages(restaurants, meetingPlace) {
  const endpoint = import.meta.env.VITE_PLACE_IMAGE_LOOKUP_URL
  if (!endpoint) return restaurants

  try {
    const data = await postPlaceImageLookup(endpoint, {
      meetingPlace,
      restaurants: restaurants.map((restaurant) => ({
        restaurantId: restaurant.id,
        placeId: restaurant.placeId ?? null,
        name: restaurant.name,
        address: restaurant.address ?? '',
        latitude: restaurant.latitude ?? null,
        longitude: restaurant.longitude ?? null,
      })),
    })
    return mergePlaceImages(restaurants, data.places ?? data.restaurants ?? [])
  } catch {
    return restaurants
  }
}
