/**
 * 프론트 지도 SDK 연결 모듈.
 * 지도 제공자를 선택한 뒤 이 파일의 provider 부분만 교체하면 됩니다.
 */

// ===== 개발 화면 확인용 장소 데이터 =====
const DEMO_PLACES = [
  {
    id: 'gangnam-11',
    provider: 'demo',
    name: '강남역 11번 출구',
    address: '서울 강남구 강남대로 396',
    latitude: 37.498095,
    longitude: 127.02761,
  },
  {
    id: 'hongdae-9',
    provider: 'demo',
    name: '홍대입구역 9번 출구',
    address: '서울 마포구 양화로 160',
    latitude: 37.556754,
    longitude: 126.923708,
  },
  {
    id: 'seongsu',
    provider: 'demo',
    name: '성수역',
    address: '서울 성동구 아차산로 100',
    latitude: 37.544581,
    longitude: 127.055961,
  },
]

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
  if (mapProvider?.searchPlaces) {
    return mapProvider.searchPlaces(query)
  }

  const normalized = query.replace(/\s/g, '')
  const matched = DEMO_PLACES.filter((place) => (
    `${place.name}${place.address}`.replace(/\s/g, '').includes(normalized)
  ))

  return matched.length ? matched : [{
    id: `demo-${query}`,
    provider: 'demo',
    name: query,
    address: '지도 SDK 연결 후 주소가 표시됩니다.',
    latitude: null,
    longitude: null,
  }]
}

// ===== 선택 장소 지도·마커 표시 및 지도 클릭 선택 연결 =====
export async function showSelectedPlaceOnMap(container, place, options = {}) {
  if (!container || !mapProvider?.showPlace) return
  await mapProvider.showPlace(container, place, options)
}
