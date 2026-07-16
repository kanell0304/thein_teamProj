import { RECOMMEND_AGAIN_ID } from '../../utils/momeokjiVote'
import './MomeokjiVoteStatus.css'

// ===== 추천 가게와 재투표 선택지를 동일한 득표 현황 행으로 변환 =====
function createVoteRows(session) {
  const restaurantRows = session.recommendations.map((restaurant) => ({
    id: restaurant.id,
    restaurant,
    voterIds: session.votes[restaurant.id] ?? [],
  }))

  // 모든 라운드에서 가게 3곳과 재투표 1곳, 총 4줄을 유지합니다.
  return [
    ...restaurantRows,
    {
      id: RECOMMEND_AGAIN_ID,
      restaurant: null,
      voterIds: session.votes[RECOMMEND_AGAIN_ID] ?? [],
    },
  ]
}

/** 모든 라운드에서 가게 3곳과 재투표를 4줄로 보여주는 투표 현황. */
function MomeokjiVoteStatus({ session, participants = [] }) {
  const rows = createVoteRows(session)
  const participantCount = session.settings.participantIds.length
  const votedParticipantIds = new Set(Object.values(session.votes).flat())
  const remainingCount = session.settings.participantIds.filter((participantId) => (
    !votedParticipantIds.has(participantId)
  )).length

  return (
    <section className="momeokji-vote-status-panel" aria-label="투표 현황">
      <div className="momeokji-vote-status-panel__header">
        <h4>▣ 투표 현황</h4>
        <strong>{remainingCount}명 남음</strong>
      </div>

      <div className="momeokji-vote-status-panel__rows">
        {rows.map(({ id, restaurant, voterIds }) => {
          const voters = participants.filter((participant) => voterIds.includes(participant.id))
          const voteRate = participantCount > 0
            ? Math.min((voterIds.length / participantCount) * 100, 100)
            : 0

          return (
            <div className="momeokji-vote-status-row" key={id}>
              <span className="momeokji-vote-status-row__thumbnail" aria-hidden="true">
                {restaurant?.imageUrl ? (
                  <img src={restaurant.imageUrl} alt="" />
                ) : (
                  <i>{restaurant?.visual ?? '↻'}</i>
                )}
              </span>
              <div
                className="momeokji-vote-status-row__result"
                role="progressbar"
                aria-label={`${restaurant?.name ?? '재투표'} ${voterIds.length}표`}
                aria-valuemin="0"
                aria-valuemax={participantCount}
                aria-valuenow={voterIds.length}
              >
                {/* ===== 참가자 수를 100%로 계산한 실제 득표 게이지 ===== */}
                <span
                  className="momeokji-vote-status-row__gauge"
                  style={{ width: `${voteRate}%` }}
                  aria-hidden="true"
                />
                <span className="momeokji-vote-status-row__content">
                  <strong>{voterIds.length}표</strong>
                  <span>{restaurant?.name ?? '재투표'}</span>
                  <span className="momeokji-vote-status-row__avatars" aria-label={`투표자 ${voters.length}명`}>
                    {voters.map((voter) => <i key={voter.id}>{voter.name.slice(0, 1)}</i>)}
                  </span>
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
