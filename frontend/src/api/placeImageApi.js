import axiosInstance from './axiosInstance'

// ===== 추천 식당 정보를 위치·이미지 조회 API에 전달 =====
export async function postPlaceImageLookup(endpoint, request, { signal } = {}) {
  const response = await axiosInstance.post(endpoint, request, {
    baseURL: '',
    signal,
  })
  return response.data
}
