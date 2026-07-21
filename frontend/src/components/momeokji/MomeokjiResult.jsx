import './MomeokjiResult.css'

/** finalNotice는 백엔드 FinalNoticeResponse, meetup은 MeetupDetailResponse(모임 목적 등 보강용, 선택)를 그대로 받는다. */
function MomeokjiResult({ finalNotice, meetup }) {
  if (!finalNotice) return null

  const meetingTime = new Date(finalNotice.meetingTime)
  const dateLabel = meetingTime.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' })
  const timeLabel = meetingTime.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })

  return (
    <dl className="momeokji-result">
      <div><dt>일시</dt><dd>{dateLabel} · {timeLabel}</dd></div>
      <div><dt>확정 장소</dt><dd>{finalNotice.restaurantName}</dd></div>
      <div><dt>주소</dt><dd>{finalNotice.roadAddress || finalNotice.address || '주소 정보 없음'}</dd></div>
      <div><dt>참여 인원</dt><dd>{finalNotice.participantCount}명</dd></div>
      {meetup?.commonOption?.purpose && (
        <div><dt>모임 목적</dt><dd>{meetup.commonOption.purpose}</dd></div>
      )}
    </dl>
  )
}

export default MomeokjiResult
