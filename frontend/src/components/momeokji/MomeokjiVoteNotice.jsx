import './MomeokjiVoteNotice.css'

// ===== 공지사항에는 상세 현황 대신 화면 연결 버튼만 표시 =====
function MomeokjiVoteNotice({ status, onOpenVote }) {
  const isClosed = status === 'CLOSED' || status === 'EXPIRED'

  return (
    <div className="momeokji-vote-notice">
      <button className="momeokji-vote-notice__button" type="button" onClick={onOpenVote}>
        {isClosed ? '투표 결과 확인하기' : '투표하러 가기'}
      </button>
    </div>
  )
}

export default MomeokjiVoteNotice
