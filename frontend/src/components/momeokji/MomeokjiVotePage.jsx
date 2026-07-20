import { useMemo, useState } from 'react'
import momeokjiIcon from '../../assets/icons/momeokji-icon.png'
import CountdownTimer from './CountdownTimer'
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
  const [isEditingVote, setIsEditingVote] = useState(false)
  const isExpired = session?.status === 'EXPIRED'
  const isClosed = session?.status === 'CLOSED' || isExpired
  const hasVoted = previousVoteIds.length > 0
  const isSubmittedView = hasVoted && !isEditingVote
  const activeSelectedIds = isSubmittedView ? previousVoteIds : selectedIds
  const isRevote = (session?.voteRound ?? 1) > 1

  // ===== 가게 3곳과 재추천을 각각 켜고 끄는 최대 4개 중복 선택 =====
  const toggleSelection = (optionId) => {
    if (isSubmittedView) return
    setSelectedIds((previous) => (
      previous.includes(optionId)
        ? previous.filter((id) => id !== optionId)
        : [...previous, optionId]
    ))
  }

  // ===== 기존 표를 입력 상태로 불러와 같은 라운드 안에서 다시 선택 =====
  const startEditingVote = () => {
    setSelectedIds(previousVoteIds)
    setIsEditingVote(true)
  }

  // ===== 최초 투표와 수정 투표를 같은 제출 함수로 전달 =====
  const submitCurrentVote = async () => {
    await onSubmit(activeSelectedIds)
    setIsEditingVote(false)
  }

  if (!open || !session) return null

  return (
    <div className="ui-layer momeokji-vote-layer" role="presentation">
      <button className="ui-backdrop" type="button" aria-label="투표 닫기" onClick={onClose} />
      <section className="ui-sheet momeokji-vote-sheet" role="dialog" aria-modal="true" aria-labelledby="momeokji-vote-title">
        <header className="ui-sheet__header momeokji-vote-sheet__header">
          <button className="ui-sheet__back" type="button" aria-label="투표 닫기" onClick={onClose}>‹</button>
          <img src={momeokjiIcon} alt="" />
          <h2 id="momeokji-vote-title">{isExpired ? '투표 종료' : isClosed ? '모먹지 결과' : '모먹지 투표'}</h2>
          {!isClosed && <CountdownTimer deadlineAt={session.deadlineAt} label="투표 마감" />}
        </header>

        <div className="ui-sheet__body momeokji-vote-sheet__body">
          {isClosed ? (
            <>
              <p className="momeokji-vote-sheet__eyebrow">{isExpired ? '시간 만료' : '투표 결과'}</p>
              <h3>{isExpired ? '투표 시간이 만료됐어요' : result?.selectedRestaurant?.name ?? '최종 장소가 결정됐어요'}</h3>
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
                onToggle={isSubmittedView ? undefined : toggleSelection}
                readOnly={isSubmittedView}
                showRecommendAgain
              />

              <MomeokjiVoteStatus session={session} participants={participants} />

              {isSubmittedView && (
                <p className="momeokji-vote-sheet__complete" role="status">
                  제출한 선택을 변경하려면 투표 다시하기를 눌러주세요.
                </p>
              )}

            </>
          )}
        </div>

        <div className="ui-sheet__footer momeokji-vote-sheet__footer">
          <button
            className="app-button app-button--primary app-button--large"
            type="button"
            disabled={!isClosed && (isResolvingVote || (!isSubmittedView && activeSelectedIds.length === 0))}
            onClick={isClosed ? onClose : isSubmittedView ? startEditingVote : submitCurrentVote}
          >
            {isClosed
              ? '확인'
              : isResolvingVote
                ? '새 가게를 찾는 중…'
                : isSubmittedView
                  ? '투표 다시하기'
                  : isEditingVote
                    ? '투표 변경하기'
                    : '투표하기'}
          </button>
        </div>
      </section>
    </div>
  )
}

export default MomeokjiVotePage
