import CountdownTimer from './CountdownTimer'
import './MomeokjiPreferenceNotice.css'

// ===== 채팅 공지 안에서 개인 조건 입력 현황과 진입 동작을 제공 =====
function MomeokjiPreferenceNotice({
  status,
  participantCount,
  submittedCount,
  deadlineAt,
  hasSubmitted,
  onOpen,
}) {
  const isGenerating = status === 'GENERATING'
  const progress = participantCount > 0
    ? Math.min(100, (submittedCount / participantCount) * 100)
    : 0

  return (
    <div className="preference-notice">
      <div className="preference-notice__header">
        <strong>{isGenerating ? '추천 식당 생성 중' : '개인 조건 입력 중'}</strong>
        {!isGenerating && <CountdownTimer deadlineAt={deadlineAt} label="입력 마감" />}
      </div>

      {!isGenerating && (
        <>
          <div
            className="preference-notice__progress"
            role="progressbar"
            aria-label="개인 조건 입력 완료율"
            aria-valuemin="0"
            aria-valuemax={participantCount}
            aria-valuenow={submittedCount}
          >
            <span style={{ width: `${progress}%` }} />
          </div>
          <p>
            <strong>{submittedCount}/{participantCount}명</strong> 입력 완료
          </p>
        </>
      )}

      <button
        className="preference-notice__button"
        type="button"
        disabled={isGenerating || hasSubmitted}
        onClick={onOpen}
      >
        {isGenerating ? '추천 식당을 찾고 있어요' : hasSubmitted ? '입력 완료' : '내 조건 입력하기'}
      </button>
    </div>
  )
}

export default MomeokjiPreferenceNotice
