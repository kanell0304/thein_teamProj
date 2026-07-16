import './MomeokjiResult.css'

function MomeokjiResult({ result }) {
  if (!result) return null
  return (
    <dl className="momeokji-result">
      <div><dt>일시</dt><dd>{result.date} · {result.timeLabel}</dd></div>
      <div><dt>지역</dt><dd>{result.place.name}</dd></div>
      <div><dt>참가자</dt><dd>{result.participantNames.join(', ')}</dd></div>
      <div><dt>테마</dt><dd>{result.themeLabel}</dd></div>
      {result.selectedRestaurant && (
        <div><dt>최종 가게</dt><dd>{result.selectedRestaurant.name}</dd></div>
      )}
      {result.decisionMethod === 'RANDOM_RESTAURANT_TIE' && (
        <div><dt>결정 방식</dt><dd>가게 공동 1등 · 무작위 결정</dd></div>
      )}
      <div><dt>메뉴</dt><dd>{result.selectedRestaurant?.menuName || result.menus.join(', ') || '미정'}</dd></div>
      <div><dt>피할 음식</dt><dd>{result.avoidFoods.join(', ') || '없음'}</dd></div>
      <div><dt>분위기</dt><dd>{result.moods.join(', ') || '상관없음'}</dd></div>
    </dl>
  )
}

export default MomeokjiResult
