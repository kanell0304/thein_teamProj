import { useState } from 'react'
import momeokjiIcon from '../../assets/icons/momeokji-icon.png'
import RestaurantVoteCards from './RestaurantVoteCards'
import MomeokjiVoteStatus from './MomeokjiVoteStatus'
import './MomeokjiVotePage.css'

/** 공지 버튼 또는 채팅 입력의 모먹지 버튼으로 여는 실제 투표 화면. round는 백엔드 RoundResponse를 그대로 받는다. */
function MomeokjiVotePage({
  open,
  round,
  onClose,
  onSubmit,
  isSubmitting = false,
}) {
  const [selectedIds, setSelectedIds] = useState([])
  const [hasVoted, setHasVoted] = useState(false)

  // ===== 가게 여러 곳을 켜고 끄는 다중 선택 =====
  const toggleSelection = (optionId) => {
    if (hasVoted) return
    setSelectedIds((previous) => (
      previous.includes(optionId)
        ? previous.filter((id) => id !== optionId)
        : [...previous, optionId]
    ))
  }

  const handleSubmit = async () => {
    if (selectedIds.length === 0 || hasVoted || isSubmitting) return
    await onSubmit(selectedIds)
    setHasVoted(true)
  }

  if (!open || !round) return null

  const restaurants = round.candidates.map((candidate) => ({
    id: String(candidate.roundCandidateId),
    name: candidate.name,
    imageUrl: candidate.imageUrl,
    priceRange: candidate.category,
  }))

  return (
    <div className="momeokji-vote-layer" role="presentation">
      <button className="momeokji-vote-backdrop" type="button" aria-label="투표 닫기" onClick={onClose} />
      <section className="momeokji-vote-sheet" role="dialog" aria-modal="true" aria-labelledby="momeokji-vote-title">
        <header className="momeokji-vote-sheet__header">
          <button type="button" aria-label="투표 닫기" onClick={onClose}>‹</button>
          <img src={momeokjiIcon} alt="" />
          <h2 id="momeokji-vote-title">모먹지 투표</h2>
        </header>

        <div className="momeokji-vote-sheet__body">
          <p className="momeokji-vote-sheet__eyebrow">{round.roundNo}차 추천 · AI 추천 가게 {round.candidates.length}곳</p>
          <h3>마음에 드는 음식점을 골라주세요</h3>
          <p className="momeokji-vote-sheet__multiple">여러 곳을 함께 선택할 수 있어요</p>

          <RestaurantVoteCards
            restaurants={restaurants}
            selectedIds={selectedIds}
            onToggle={hasVoted ? undefined : toggleSelection}
            readOnly={hasVoted}
          />

          <MomeokjiVoteStatus candidates={round.candidates} totalParticipants={round.participantCount} />

          {hasVoted && (
            <p className="momeokji-vote-sheet__complete" role="status">
              투표를 완료했어요. 다른 참가자의 투표를 기다리고 있어요.
            </p>
          )}
        </div>

        <div className="momeokji-vote-sheet__footer">
          <button
            className="app-button app-button--primary app-button--large"
            type="button"
            disabled={selectedIds.length === 0 || hasVoted || isSubmitting}
            onClick={handleSubmit}
          >
            {isSubmitting ? '제출 중...' : hasVoted ? '투표 완료' : '투표하기'}
          </button>
        </div>
      </section>
    </div>
  )
}

export default MomeokjiVotePage
