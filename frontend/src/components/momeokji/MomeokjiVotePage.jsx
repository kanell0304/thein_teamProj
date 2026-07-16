import { useMemo, useState } from 'react'
import momeokjiIcon from '../../assets/icons/momeokji-icon.png'
import RestaurantVoteCards from './RestaurantVoteCards'
import MomeokjiResult from './MomeokjiResult'
import MomeokjiVoteStatus from './MomeokjiVoteStatus'
import './MomeokjiVotePage.css'

/** 공지 버튼 또는 채팅 입력의 모먹지 버튼으로 여는 실제 투표 화면. */
function MomeokjiVotePage({
  open,
  session,
  currentUserId,
  participants = [],
  onClose,
  onSubmit,
  isResolvingVote = false,
  result,
}) {
  const previousVoteIds = useMemo(() => (
    Object.entries(session?.votes ?? {}).filter(([, voterIds]) => (
      voterIds.includes(currentUserId)
    )).map(([optionId]) => optionId)
  ), [currentUserId, session])
  const [selectedIds, setSelectedIds] = useState([])
  const isClosed = session?.status === 'CLOSED'
  const hasVoted = previousVoteIds.length > 0
  const activeSelectedIds = hasVoted ? previousVoteIds : selectedIds
  const isRevote = (session?.voteRound ?? 1) > 1

  // ===== 가게 3곳과 재추천을 각각 켜고 끄는 최대 4개 중복 선택 =====
  const toggleSelection = (optionId) => {
    if (hasVoted) return
    setSelectedIds((previous) => (
      previous.includes(optionId)
        ? previous.filter((id) => id !== optionId)
        : [...previous, optionId]
    ))
  }

  if (!open || !session) return null

  return (
    <div className="momeokji-vote-layer" role="presentation">
      <button className="momeokji-vote-backdrop" type="button" aria-label="투표 닫기" onClick={onClose} />
      <section className="momeokji-vote-sheet" role="dialog" aria-modal="true" aria-labelledby="momeokji-vote-title">
        <header className="momeokji-vote-sheet__header">
          <button type="button" aria-label="투표 닫기" onClick={onClose}>‹</button>
          <img src={momeokjiIcon} alt="" />
          <h2 id="momeokji-vote-title">{isClosed ? '모먹지 결과' : '모먹지 투표'}</h2>
        </header>

        <div className="momeokji-vote-sheet__body">
          {isClosed ? (
            <>
              <p className="momeokji-vote-sheet__eyebrow">투표 결과</p>
              <h3>{result?.selectedRestaurant?.name ?? '최종 장소가 결정됐어요'}</h3>
              <MomeokjiResult result={result} />
            </>
          ) : (
            <>
              <p className="momeokji-vote-sheet__eyebrow">
                {isRevote ? `${session.voteRound}차 투표 · AI 새 추천 3곳` : 'AI 추천 가게 3곳'}
              </p>
              <h3>마음에 드는 음식점을 골라주세요</h3>
              <p className="momeokji-vote-sheet__multiple">
                가게 3곳과 재투표 중 원하는 항목을 모두 선택할 수 있어요 (최대 4개)
              </p>
              <p className="momeokji-vote-sheet__description">
                {session.settings.place.name} 주변 · {session.settings.date} {session.settings.timeLabel}
              </p>
              <RestaurantVoteCards
                restaurants={session.recommendations}
                selectedIds={activeSelectedIds}
                onToggle={hasVoted ? undefined : toggleSelection}
                readOnly={hasVoted}
                showRecommendAgain
              />

              <MomeokjiVoteStatus session={session} participants={participants} />

              {hasVoted && (
                <p className="momeokji-vote-sheet__complete" role="status">
                  투표를 완료했어요. 다른 참가자의 투표를 기다리고 있어요.
                </p>
              )}

            </>
          )}
        </div>

        <div className="momeokji-vote-sheet__footer">
          <button
            className="app-button app-button--primary app-button--large"
            type="button"
            disabled={!isClosed && (activeSelectedIds.length === 0 || hasVoted || isResolvingVote)}
            onClick={isClosed ? onClose : () => onSubmit(activeSelectedIds)}
          >
            {isClosed
              ? '확인'
              : isResolvingVote
                ? '새 가게를 찾는 중…'
                : hasVoted
                  ? '투표 완료'
                  : '투표하기'}
          </button>
        </div>
      </section>
    </div>
  )
}

export default MomeokjiVotePage
