import { useRef } from 'react'
import './RestaurantVoteCards.css'
import { RECOMMEND_AGAIN_ID } from '../../utils/momeokjiVote'

/**
 * AI 추천 가게 3개를 카드로 보여주는 공통 컴포넌트.
 * 공지 미리보기에서는 readOnly, 투표 화면에서는 선택형으로 사용합니다.
 */
function RestaurantVoteCards({
  restaurants,
  selectedIds = [],
  onToggle,
  readOnly = false,
  showRecommendAgain = false,
}) {
  const carouselRef = useRef(null)
  const dragStateRef = useRef({
    isDragging: false,
    moved: false,
    startX: 0,
    startScrollLeft: 0,
    suppressClickUntil: 0,
  })

  // ===== 가게 3곳과 재투표를 같은 슬라이드 카드 데이터로 구성 =====
  const voteOptions = showRecommendAgain
    ? [
        ...restaurants,
        {
          id: RECOMMEND_AGAIN_ID,
          name: '재투표',
          priceRange: '새 가게 3곳',
          visual: '↻',
          isRecommendAgain: true,
        },
      ]
    : restaurants

  // ===== 데스크톱 마우스로 누른 채 움직이는 가로 슬라이드 =====
  const startMouseDrag = (event) => {
    if (event.pointerType !== 'mouse' || event.button !== 0) return

    const carousel = carouselRef.current
    if (!carousel) return

    dragStateRef.current = {
      isDragging: true,
      moved: false,
      startX: event.clientX,
      startScrollLeft: carousel.scrollLeft,
      suppressClickUntil: dragStateRef.current.suppressClickUntil,
    }
  }

  const moveMouseDrag = (event) => {
    const carousel = carouselRef.current
    const dragState = dragStateRef.current
    if (!carousel || !dragState.isDragging) return

    const distance = event.clientX - dragState.startX
    if (Math.abs(distance) > 4 && !dragState.moved) {
      dragState.moved = true
      carousel.setPointerCapture(event.pointerId)
      carousel.classList.add('is-dragging')
    }
    if (!dragState.moved) return

    carousel.scrollLeft = dragState.startScrollLeft - distance
    event.preventDefault()
  }

  const finishMouseDrag = (event) => {
    const carousel = carouselRef.current
    if (!carousel || !dragStateRef.current.isDragging) return

    const dragState = dragStateRef.current
    dragState.isDragging = false
    dragState.suppressClickUntil = dragState.moved ? event.timeStamp + 300 : 0
    dragState.moved = false
    carousel.classList.remove('is-dragging')
    if (carousel.hasPointerCapture(event.pointerId)) {
      carousel.releasePointerCapture(event.pointerId)
    }
  }

  // 마우스로 카드를 끌었을 때 포인터 해제 뒤 발생하는 클릭은 선택으로 처리하지 않습니다.
  const selectVoteOption = (event, optionId) => {
    if (event.timeStamp <= dragStateRef.current.suppressClickUntil) return
    onToggle?.(optionId)
  }

  return (
    <div
      className="restaurant-vote-cards"
      aria-label="투표 선택지 목록"
      ref={carouselRef}
      onPointerDown={startMouseDrag}
      onPointerMove={moveMouseDrag}
      onPointerUp={finishMouseDrag}
      onPointerCancel={finishMouseDrag}
    >
      {voteOptions.map((restaurant) => {
        const isSelected = selectedIds.includes(restaurant.id)

        return (
          <button
            className={`restaurant-vote-card${restaurant.isRecommendAgain ? ' is-recommend-again' : ''}${isSelected ? ' is-selected' : ''}`}
            type="button"
            aria-pressed={readOnly ? undefined : isSelected}
            disabled={readOnly}
            key={restaurant.id}
            onClick={(event) => selectVoteOption(event, restaurant.id)}
          >
            {restaurant.imageUrl ? (
              <img className="restaurant-vote-card__image" src={restaurant.imageUrl} alt="" />
            ) : (
              <span className="restaurant-vote-card__fallback" aria-hidden="true">
                {restaurant.visual || '🍽️'}
              </span>
            )}
            <span className="restaurant-vote-card__shade" aria-hidden="true" />
            <span className="restaurant-vote-card__badge">
              {restaurant.isRecommendAgain ? '↻ 재추천' : '● 추천'}
            </span>
            <span className="restaurant-vote-card__copy">
              <strong>{restaurant.name}</strong>
              <small>{restaurant.priceRange || restaurant.distanceLabel}</small>
              {restaurant.reason && (
                <p className="restaurant-vote-card__reason">{restaurant.reason}</p>
              )}
            </span>
          </button>
        )
      })}
    </div>
  )
}

export default RestaurantVoteCards
