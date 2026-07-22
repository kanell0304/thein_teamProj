import './MomeokjiResult.css'

// ===== 확정된 식당 좌표를 카카오맵 공유 링크로 변환 =====
function createKakaoMapUrl(restaurant) {
  if (!restaurant) return ''

  const latitude = Number(restaurant.latitude)
  const longitude = Number(restaurant.longitude)
  if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
    return `https://map.kakao.com/link/map/${encodeURIComponent(restaurant.name)},${latitude},${longitude}`
  }

  const query = [restaurant.name, restaurant.address].filter(Boolean).join(' ')
  return `https://map.kakao.com/link/search/${encodeURIComponent(query)}`
}

function MomeokjiResult({ result }) {
  if (!result) return null
  const restaurant = result.selectedRestaurant
  const kakaoMapUrl = createKakaoMapUrl(restaurant)

  return (
    <div className="momeokji-result-wrap">
      {restaurant && (
        <a
          className="momeokji-result-place"
          href={kakaoMapUrl}
          target="_blank"
          rel="noreferrer"
          aria-label={`${restaurant.name} 카카오맵에서 보기`}
        >
          <span className="momeokji-result-place__media">
            <span aria-hidden="true">🍽️</span>
            {restaurant.imageUrl && (
              <img
                src={restaurant.imageUrl}
                alt={`${restaurant.name} 추천 이미지`}
                onError={(event) => { event.currentTarget.hidden = true }}
              />
            )}
          </span>
          <span className="momeokji-result-place__content">
            <strong>{restaurant.name}</strong>
            <span>{restaurant.address || result.place.name}</span>
            <em>카카오맵에서 보기 <span aria-hidden="true">›</span></em>
          </span>
        </a>
      )}

      <dl className="momeokji-result">
        <div><dt>일시</dt><dd>{result.date} · {result.timeLabel}</dd></div>
        <div><dt>지역</dt><dd>{result.place.name}</dd></div>
        <div><dt>참가자</dt><dd>{result.participantNames.join(', ')}</dd></div>
        <div><dt>테마</dt><dd>{result.themeLabel}</dd></div>
        {restaurant && (
          <div>
            <dt>최종 가게</dt>
            <dd>{restaurant.name}</dd>
          </div>
        )}
        {result.decisionMethod === 'RANDOM_RESTAURANT_TIE' && (
          <div><dt>결정 방식</dt><dd>가게 공동 1등 · 무작위 결정</dd></div>
        )}
        {result.decisionMethod === 'DEADLINE_MAJORITY' && (
          <div><dt>결정 방식</dt><dd>투표 시간 만료 · 최다 득표</dd></div>
        )}
        {result.decisionMethod === 'DEADLINE_RANDOM_RESTAURANT_TIE' && (
          <div><dt>결정 방식</dt><dd>투표 시간 만료 · 공동 1등 무작위 결정</dd></div>
        )}
        {result.decisionMethod === 'NO_VOTES_TIMEOUT' && (
          <div><dt>결정 방식</dt><dd>투표 시간 만료 · 참여자 투표 없음</dd></div>
        )}
        <div><dt>메뉴</dt><dd>{restaurant?.menuName || result.menus.join(', ') || '미정'}</dd></div>
        <div><dt>피할 음식</dt><dd>{result.avoidFoods.join(', ') || '없음'}</dd></div>
        <div><dt>분위기</dt><dd>{result.moods.join(', ') || '상관없음'}</dd></div>
      </dl>
    </div>
  )
}

export default MomeokjiResult
