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

    showSelectedPlaceOnMap(mapContainerRef.current, value).catch(() => {
      if (isActive) {
        setSearchError('카카오 지도를 표시하지 못했어요. JavaScript 키와 등록 도메인을 확인해주세요.')
      }
    })

    return () => {
      isActive = false
    }
  }, [value])

  const handleSearch = async () => {
    if (!query.trim()) return
    setIsSearching(true)
    setSearchError('')
    try {
      setResults(await searchPlacesWithMapApi(query.trim()))
    } catch {
      setResults([])
      setSearchError('장소를 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setIsSearching(false)
    }
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
            <button type="button" key={place.id} onClick={() => onChange(place)}>
              <strong>{place.name}</strong><span>{place.address}</span>
            </button>
          ))}
        </div>
      )}
      {searchError && <p className="place-picker__error" role="alert">{searchError}</p>}
      <div className={`place-picker__map${isRealMapReady ? ' is-ready' : ''}`} ref={mapContainerRef} aria-label="카카오 지도 표시 영역">
        {(!isRealMapReady || !value) && (
          <div className="place-picker__placeholder">
            <span className="place-picker__pin" aria-hidden="true">●</span>
            <span>{value ? value.name : '장소를 선택하면 지도에 표시돼요'}</span>
            {!isRealMapReady && <small>카카오맵 키 설정 전 데모 영역</small>}
          </div>
        )}
      </div>
    </div>
  )
}

export default PlacePicker
