import axiosInstance from './axiosInstance'

// ===== 모임 조건을 AI 식당 추천 API에 전달 =====
export async function postRestaurantRecommendation(endpoint, request, { signal } = {}) {
  const response = await axiosInstance.post(endpoint, request, {
    baseURL: '',
    signal,
  })
  return response.data
}
