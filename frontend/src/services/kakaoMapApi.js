/**
 * 카카오맵 JavaScript SDK 연결 어댑터.
 * 카카오 SDK 형식을 앱 공통 장소 데이터 형식으로 변환합니다.
 */

import { registerMapProvider } from './mapApi'

// ===== 카카오 SDK 환경 설정 =====
const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY?.trim()
const KAKAO_SDK_ID = 'kakao-map-sdk'
const ADDRESS_PATTERN = /(?:대로|로|길)\s*\d+(?:-\d+)?(?:\s|$)|(?:읍|면|동|리)\s*\d+(?:-\d+)?(?:\s|$)/

let sdkLoadPromise = null
const mapInstances = new WeakMap()

// ===== 카카오 JavaScript SDK 지연 로딩 =====
function loadKakaoMapSdk() {
  if (window.kakao?.maps?.services) {
    return Promise.resolve(window.kakao)
  }

  if (!KAKAO_APP_KEY) {
    return Promise.reject(new Error('VITE_KAKAO_MAP_APP_KEY가 설정되지 않았습니다.'))
  }

  if (sdkLoadPromise) return sdkLoadPromise

  sdkLoadPromise = new Promise((resolve, reject) => {
    const handleLoad = () => {
      if (!window.kakao?.maps) {
        reject(new Error('카카오맵 SDK를 불러오지 못했습니다.'))
        return
      }

      window.kakao.maps.load(() => resolve(window.kakao))
    }

    const existingScript = document.getElementById(KAKAO_SDK_ID)
    if (existingScript) {
      existingScript.addEventListener('load', handleLoad, { once: true })
      existingScript.addEventListener('error', () => reject(new Error('카카오맵 SDK 요청에 실패했습니다.')), { once: true })
      return
    }

    const script = document.createElement('script')
    script.id = KAKAO_SDK_ID
    script.async = true
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(KAKAO_APP_KEY)}&autoload=false&libraries=services`
    script.addEventListener('load', handleLoad, { once: true })
    script.addEventListener('error', () => reject(new Error('카카오맵 SDK 요청에 실패했습니다.')), { once: true })
    document.head.appendChild(script)
  })

  return sdkLoadPromise
}

// ===== 카카오 장소 검색 결과를 앱 데이터로 변환 =====
function normalizeKakaoPlace(place) {
  return {
    id: place.id,
    provider: 'kakao',
    name: place.place_name,
    address: place.road_address_name || place.address_name,
    latitude: Number(place.y),
    longitude: Number(place.x),
    categoryName: place.category_name,
    phone: place.phone,
    placeUrl: place.place_url,
  }
}

// ===== 카카오 주소 검색 결과를 앱 데이터로 변환 =====
function normalizeKakaoAddress(address) {
  const roadAddress = address.road_address
  const displayAddress = roadAddress?.address_name || address.address?.address_name || address.address_name
  const displayName = roadAddress?.building_name || displayAddress

  return {
    id: `address-${address.x}-${address.y}`,
    provider: 'kakao-address',
    name: displayName,
    address: displayAddress,
    latitude: Number(address.y),
    longitude: Number(address.x),
    categoryName: '주소',
    phone: '',
    placeUrl: '',
  }
}

// ===== 키워드 기반 장소명·상호명 검색 =====
function searchKeywordPlaces(kakao, query) {
  const placesService = new kakao.maps.services.Places()

  return new Promise((resolve, reject) => {
    placesService.keywordSearch(query, (places, status) => {
      if (status === kakao.maps.services.Status.OK) {
        resolve(places.map(normalizeKakaoPlace))
        return
      }

      if (status === kakao.maps.services.Status.ZERO_RESULT) {
        resolve([])
        return
      }

      reject(new Error('카카오 장소 검색에 실패했습니다.'))
    })
  })
}

// ===== 도로명·지번 주소를 좌표로 변환 =====
function searchAddressCoordinates(kakao, query) {
  const geocoder = new kakao.maps.services.Geocoder()

  return new Promise((resolve, reject) => {
    geocoder.addressSearch(query, (addresses, status) => {
      if (status === kakao.maps.services.Status.OK) {
        resolve(addresses.map(normalizeKakaoAddress))
        return
      }

      if (status === kakao.maps.services.Status.ZERO_RESULT) {
        resolve([])
        return
      }

      reject(new Error('카카오 주소 검색에 실패했습니다.'))
    })
  })
}

// ===== 입력 형태에 따라 장소명 검색과 주소 검색 순서 결정 =====
async function searchPlaces(query) {
  const kakao = await loadKakaoMapSdk()
  const shouldSearchAddressFirst = ADDRESS_PATTERN.test(query)

  if (shouldSearchAddressFirst) {
    const addressResults = await searchAddressCoordinates(kakao, query)
    if (addressResults.length > 0) return addressResults
  }

  const keywordResults = await searchKeywordPlaces(kakao, query)

  if (keywordResults.length > 0) return keywordResults
  if (shouldSearchAddressFirst) return []
  return searchAddressCoordinates(kakao, query)
}

// ===== 선택한 장소의 지도와 마커 표시 =====
async function showPlace(container, place) {
  if (!container || place.latitude == null || place.longitude == null) return

  const kakao = await loadKakaoMapSdk()
  const position = new kakao.maps.LatLng(place.latitude, place.longitude)
  let instance = mapInstances.get(container)

  if (!instance) {
    const map = new kakao.maps.Map(container, {
      center: position,
      level: 4,
    })
    const marker = new kakao.maps.Marker({ position, map })
    instance = { map, marker }
    mapInstances.set(container, instance)
  } else {
    instance.marker.setPosition(position)
    instance.marker.setMap(instance.map)
    instance.map.setCenter(position)
  }

  // 바텀시트 크기 변화 뒤에도 지도 타일과 중심점을 다시 맞춥니다.
  instance.map.relayout()
  instance.map.setCenter(position)
}

// ===== 앱 공통 지도 인터페이스에 카카오 공급자 등록 =====
export function initializeKakaoMapProvider() {
  if (!KAKAO_APP_KEY) return false

  registerMapProvider({
    searchPlaces,
    showPlace,
  })
  return true
}
