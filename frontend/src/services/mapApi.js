/**
 * 프론트 지도 SDK 연결 모듈.
 * 지도 제공자를 선택한 뒤 이 파일의 provider 부분만 교체하면 됩니다.
 */

// ===== 지도 제공자 연결 지점 =====
// 카카오맵 어댑터가 아래 공통 형식의 객체를 등록합니다.
let mapProvider = null

export function registerMapProvider(provider) {
  mapProvider = provider
}

export function isMapProviderReady() {
  return mapProvider != null
}

// ===== 프론트 SDK 장소 검색 =====
export async function searchPlacesWithMapApi(query) {
  if (!mapProvider?.searchPlaces) {
    throw new Error('카카오 지도 연결이 필요합니다.')
  }
  return mapProvider.searchPlaces(query)
}

// ===== 선택 장소 지도·마커 표시 및 지도 클릭 선택 연결 =====
export async function showSelectedPlaceOnMap(container, place, options = {}) {
  if (!container || !mapProvider?.showPlace) return
  await mapProvider.showPlace(container, place, options)
}
