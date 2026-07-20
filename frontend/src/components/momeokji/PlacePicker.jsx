import { useEffect, useRef, useState } from 'react'
import {
  isMapProviderReady,
  searchPlacesWithMapApi,
  showSelectedPlaceOnMap,
} from '../../services/mapApi'
import './PlacePicker.css'

function PlacePicker({ value, onChange }) {
  const mapContainerRef = useRef(null)
  const isRealMapReady = isMapProviderReady()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [isSearching, setIsSearching] = useState(false)
  const [searchError, setSearchError] = useState('')

  // ===== 선택 장소를 지도 SDK의 마커로 표시 =====
  useEffect(() => {
    let isActive = true

    showSelectedPlaceOnMap(mapContainerRef.current, value, {
      onSelect: (selectedPlace) => {
        if (!isActive) return
        setQuery(selectedPlace.address)
        setResults([])
        setSearchError('')
        onChange(selectedPlace)
      },
      onError: () => {
        if (isActive) setSearchError('선택한 지도 위치의 주소를 찾지 못했어요.')
      },
    }).catch(() => {
      if (isActive) {
        setSearchError('카카오 지도를 표시하지 못했어요. JavaScript 키와 등록 도메인을 확인해주세요.')
      }
    })

    return () => {
      isActive = false
    }
  }, [onChange, value])

  const handleSearch = async () => {
    if (!query.trim()) return
    setIsSearching(true)
    setSearchError('')
    try {
      const searchResults = await searchPlacesWithMapApi(query.trim())
      setResults(searchResults)
      if (searchResults.length === 0) {
        setSearchError('검색 결과가 없어요. 주소 순서를 바꾸거나 지도에서 직접 선택해주세요.')
      }
    } catch {
      setResults([])
      setSearchError('장소를 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setIsSearching(false)
    }
  }

  // ===== 검색 결과와 지도 클릭 결과를 동일한 장소 선택 데이터로 전달 =====
  const handleSelectPlace = (place) => {
    setQuery(place.address || place.name)
    setResults([])
    setSearchError('')
    onChange(place)
  }

  return (
    <div className="place-picker">
      <div className="place-picker__search">
        <input value={query} aria-label="장소 검색" placeholder="장소 또는 주소 검색" onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') handleSearch() }} />
        <button
          className="app-button app-button--primary app-button--small"
          type="button"
          disabled={!query.trim() || isSearching}
          onClick={handleSearch}
        >
          {isSearching ? '검색 중' : '검색'}
        </button>
      </div>
      {results.length > 0 && (
        <div className="place-picker__results">
          {results.map((place) => (
            <button type="button" key={place.id} onClick={() => handleSelectPlace(place)}>
              <strong>{place.name}</strong><span>{place.address}</span>
            </button>
          ))}
        </div>
      )}
      {searchError && <p className="place-picker__error" role="alert">{searchError}</p>}
      <div className={`place-picker__map${isRealMapReady ? ' is-ready' : ''}`} ref={mapContainerRef} aria-label="카카오 지도 표시 영역">
        {!isRealMapReady && (
          <div className="place-picker__placeholder">
            <span className="place-picker__pin" aria-hidden="true">●</span>
            <span>{value ? value.name : '장소를 선택하면 지도에 표시돼요'}</span>
            <small>카카오맵 키 설정 전 데모 영역</small>
          </div>
        )}
        {isRealMapReady && !value && (
          <span className="place-picker__map-hint">지도를 눌러 장소를 선택할 수 있어요</span>
        )}
      </div>
    </div>
  )
}

export default PlacePicker
