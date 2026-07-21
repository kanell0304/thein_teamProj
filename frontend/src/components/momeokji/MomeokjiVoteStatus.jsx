import './MomeokjiVoteStatus.css'

/**
 * 후보별 실시간 득표 현황. candidates는 백엔드 CandidateSummary(voteCount 포함)를 그대로 받는다.
 * 백엔드가 "누가 투표했는지"는 주지 않고 집계만 주므로, 투표자 아바타 없이 게이지+득표수만 보여준다.
 */
function MomeokjiVoteStatus({ candidates = [], totalParticipants = 0 }) {
  return (
    <section className="momeokji-vote-status-panel" aria-label="투표 현황">
      <div className="momeokji-vote-status-panel__header">
        <h4>▣ 투표 현황</h4>
      </div>

      <div className="momeokji-vote-status-panel__rows">
        {candidates.map((candidate) => {
          const voteRate = totalParticipants > 0
            ? Math.min((candidate.voteCount / totalParticipants) * 100, 100)
            : 0

          return (
            <div className="momeokji-vote-status-row" key={candidate.roundCandidateId}>
              <span className="momeokji-vote-status-row__thumbnail" aria-hidden="true">
                {candidate.imageUrl ? <img src={candidate.imageUrl} alt="" /> : <i>🍽️</i>}
              </span>
              <div
                className="momeokji-vote-status-row__result"
                role="progressbar"
                aria-label={`${candidate.name} ${candidate.voteCount}표`}
                aria-valuemin="0"
                aria-valuemax={totalParticipants}
                aria-valuenow={candidate.voteCount}
              >
                {/* ===== 참가자 수를 100%로 계산한 실제 득표 게이지 ===== */}
                <span
                  className="momeokji-vote-status-row__gauge"
                  style={{ width: `${voteRate}%` }}
                  aria-hidden="true"
                />
                <span className="momeokji-vote-status-row__content">
                  <strong>{candidate.voteCount}표</strong>
                  <span>{candidate.name}</span>
                </span>
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}

export default MomeokjiVoteStatus
