import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import momeokjiIcon from '../assets/icons/momeokji-icon.png'
import AddableMenuInput from '../components/momeokji/AddableMenuInput'
import CountdownTimer from '../components/momeokji/CountdownTimer'
import { AVOID_OPTIONS, MOOD_OPTIONS } from '../constants/momeokjiOptions'
import './ParticipantPreferencePage.css'

const PARKING_OPTIONS = [
  { value: 'REQUIRED', label: '주차 필요' },
  { value: 'NOT_REQUIRED', label: '주차 불필요' },
  { value: 'ANY', label: '상관없어요' },
]

const BUDGET_OPTIONS = [
  { value: '10000', label: '10,000원' },
  { value: '15000', label: '15,000원' },
  { value: '20000', label: '20,000원' },
  { value: '30000', label: '30,000원' },
  { value: 'UNRESTRICTED', label: '상관없어요' },
]

// ===== 저장값은 숫자만 유지하고 입력창에는 천 단위 쉼표로 표시 =====
function formatBudgetInput(value) {
  if (!value) return ''
  return Number(value).toLocaleString('ko-KR')
}

// ===== 키보드가 덮어쓰는 환경까지 포함한 실제 표시 가능 영역 계산 =====
function getParticipantViewportMetrics() {
  if (typeof window === 'undefined') {
    return { visibleHeight: 812, bottomInset: 0 }
  }

  const viewport = window.visualViewport
  const viewportTop = viewport?.offsetTop ?? 0
  const viewportBottom = viewportTop + (viewport?.height ?? window.innerHeight)
  const chatRoomRect = document.querySelector('.chat-room')?.getBoundingClientRect()
  const containerTop = chatRoomRect?.top ?? 0
  const containerBottom = chatRoomRect?.bottom ?? window.innerHeight
  const visibleTop = Math.max(containerTop, viewportTop)
  const visibleBottom = Math.min(containerBottom, viewportBottom)

  return {
    visibleHeight: Math.max(0, Math.round(visibleBottom - visibleTop)),
    bottomInset: Math.max(0, Math.round(containerBottom - visibleBottom)),
  }
}

// ===== 단일·다중 선택을 동일한 주황색 선택 상태로 표시하는 개인 조건 칩 =====
function PreferenceChips({ label, options, selected, onToggle, single = false }) {
  return (
    <div className="ui-chip-group participant-preference-chips" aria-label={label}>
      {options.map((option) => {
        const value = typeof option === 'string' ? option : option.value
        const optionLabel = typeof option === 'string' ? option : option.label
        const isSelected = single ? selected === value : selected.includes(value)

        return (
          <button
            className={`ui-chip${isSelected ? ' is-selected' : ''}`}
            type="button"
            aria-pressed={isSelected}
            key={value}
            onClick={() => onToggle(value)}
          >
            {optionLabel}
          </button>
        )
      })}
    </div>
  )
}

/** 선택된 참가자에게 전달되는 개인 필수·선택 조건 입력 화면. */
function ParticipantPreferencePage({
  open,
  onClose,
  onSubmit,
  onDecline,
  participant,
  meetingSummary,
  deadlineAt,
}) {
  const [customAvoidOptions, setCustomAvoidOptions] = useState([])
  const [avoidFoods, setAvoidFoods] = useState([])
  const [avoidInput, setAvoidInput] = useState('')
  const [budgetInput, setBudgetInput] = useState('20000')
  const [budgetUnrestricted, setBudgetUnrestricted] = useState(false)
  const [myDataConsent, setMyDataConsent] = useState(null)
  const [parkingPreference, setParkingPreference] = useState('ANY')
  const [moods, setMoods] = useState([])
  const [validationMessage, setValidationMessage] = useState('')
  const [validationField, setValidationField] = useState('')
  const [showMissingChoice, setShowMissingChoice] = useState(false)
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [dragOffset, setDragOffset] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const [viewportMetrics, setViewportMetrics] = useState(getParticipantViewportMetrics)
  const sectionRefs = useRef({})
  const sheetRef = useRef(null)
  const dragStateRef = useRef(null)
  const didDragRef = useRef(false)

  // ===== 다시 열 때 펼쳐진 상태가 되도록 닫기 전에 시트 위치를 초기화 =====
  const closePreferencePage = useCallback(() => {
    setIsCollapsed(false)
    setDragOffset(null)
    setIsDragging(false)
    onClose()
  }, [onClose])

  const selectableAvoidOptions = useMemo(
    () => [...new Set([...AVOID_OPTIONS, ...customAvoidOptions])],
    [customAvoidOptions],
  )

  useEffect(() => {
    if (!open) return undefined
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') {
        if (showMissingChoice) setShowMissingChoice(false)
        else closePreferencePage()
      }
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [closePreferencePage, open, showMissingChoice])

  // ===== 모바일 키보드·주소창으로 visual viewport가 변하면 시트 크기와 위치 동기화 =====
  useEffect(() => {
    const updateViewportMetrics = () => {
      const nextMetrics = getParticipantViewportMetrics()
      setViewportMetrics((previous) => (
        previous.visibleHeight === nextMetrics.visibleHeight
          && previous.bottomInset === nextMetrics.bottomInset
          ? previous
          : nextMetrics
      ))
    }

    const viewport = window.visualViewport
    viewport?.addEventListener('resize', updateViewportMetrics)
    viewport?.addEventListener('scroll', updateViewportMetrics)
    window.addEventListener('resize', updateViewportMetrics)

    return () => {
      viewport?.removeEventListener('resize', updateViewportMetrics)
      viewport?.removeEventListener('scroll', updateViewportMetrics)
      window.removeEventListener('resize', updateViewportMetrics)
    }
  }, [])

  // ===== 상단 손잡이의 포인터 이동량으로 시트를 위·아래 스냅 =====
  const prepareSheetDrag = (clientY, pointerId) => {
    const sheetHeight = sheetRef.current?.offsetHeight ?? 0
    const maxOffset = Math.max(0, sheetHeight - 36)
    dragStateRef.current = {
      pointerId,
      startY: clientY,
      baseOffset: isCollapsed ? maxOffset : 0,
      maxOffset,
    }
    didDragRef.current = false
    setIsDragging(true)
  }

  const updateSheetDrag = (clientY, pointerId) => {
    const dragState = dragStateRef.current
    if (!dragState || dragState.pointerId !== pointerId) return

    const deltaY = clientY - dragState.startY
    if (Math.abs(deltaY) > 6) didDragRef.current = true
    setDragOffset(Math.min(
      dragState.maxOffset,
      Math.max(0, dragState.baseOffset + deltaY),
    ))
  }

  const completeSheetDrag = (clientY, pointerId) => {
    const dragState = dragStateRef.current
    if (!dragState || dragState.pointerId !== pointerId) return

    const deltaY = clientY - dragState.startY
    if (didDragRef.current) {
      setIsCollapsed(dragState.baseOffset === 0 ? deltaY > 64 : !(deltaY < -48))
    }
    dragStateRef.current = null
    setDragOffset(null)
    setIsDragging(false)
  }

  const cancelSheetDrag = () => {
    dragStateRef.current = null
    didDragRef.current = false
    setDragOffset(null)
    setIsDragging(false)
  }

  const toggleCollapsed = () => {
    if (didDragRef.current) {
      didDragRef.current = false
      return
    }
    setIsCollapsed((previous) => !previous)
  }

  // ===== 터치·펜은 포인터 캡처로 시트 바깥 이동까지 계속 추적 =====
  const startSheetPointerDrag = (event) => {
    if (event.pointerType === 'mouse') return
    prepareSheetDrag(event.clientY, event.pointerId)
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  const moveSheetPointerDrag = (event) => {
    if (event.pointerType === 'mouse') return
    updateSheetDrag(event.clientY, event.pointerId)
  }

  const finishSheetPointerDrag = (event) => {
    if (event.pointerType === 'mouse') return
    completeSheetDrag(event.clientY, event.pointerId)
    event.currentTarget.releasePointerCapture?.(event.pointerId)
  }

  // ===== 데스크톱 마우스는 창 단위 이동·놓기 이벤트로 안정적으로 추적 =====
  const startSheetMouseDrag = (event) => {
    event.preventDefault()
    const mousePointerId = 'mouse'
    prepareSheetDrag(event.clientY, mousePointerId)

    const moveWithMouse = (moveEvent) => {
      updateSheetDrag(moveEvent.clientY, mousePointerId)
    }
    const finishWithMouse = (upEvent) => {
      completeSheetDrag(upEvent.clientY, mousePointerId)
      window.removeEventListener('mousemove', moveWithMouse)
      window.removeEventListener('mouseup', finishWithMouse)
    }

    window.addEventListener('mousemove', moveWithMouse)
    window.addEventListener('mouseup', finishWithMouse)
  }

  // ===== 검증 실패 시 첫 번째 미입력 항목을 화면 중앙으로 이동하고 입력 위치를 안내 =====
  const showFieldError = (field, message) => {
    setValidationField(field)
    setValidationMessage(message)
    requestAnimationFrame(() => {
      const section = sectionRefs.current[field]
      section?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      section?.querySelector('input, button')?.focus({ preventScroll: true })
    })
  }

  const clearFieldError = (field) => {
    if (validationField !== field) return
    setValidationField('')
    setValidationMessage('')
  }

  // ===== '없어요'와 실제 제외 음식이 동시에 선택되지 않도록 정리 =====
  const toggleAvoidFood = (value) => {
    clearFieldError('avoidFoods')
    setAvoidFoods((previous) => {
      if (value === '없어요') return previous.includes(value) ? [] : ['없어요']
      const withoutNone = previous.filter((item) => item !== '없어요')
      return withoutNone.includes(value)
        ? withoutNone.filter((item) => item !== value)
        : [...withoutNone, value]
    })
  }

  // ===== 직접 입력한 제외 음식을 새 칩으로 추가하고 즉시 선택 =====
  const addCustomAvoidFood = () => {
    const value = avoidInput.trim()
    if (!value) return

    setCustomAvoidOptions((previous) => (
      AVOID_OPTIONS.includes(value) || previous.includes(value)
        ? previous
        : [...previous, value]
    ))
    setAvoidFoods((previous) => [
      ...previous.filter((item) => item !== '없어요' && item !== value),
      value,
    ])
    setAvoidInput('')
    clearFieldError('avoidFoods')
  }

  const toggleMood = (value) => {
    setMoods((previous) => {
      if (value === '상관없어요') return previous.includes(value) ? [] : ['상관없어요']
      const withoutAny = previous.filter((item) => item !== '상관없어요')
      return withoutAny.includes(value)
        ? withoutAny.filter((item) => item !== value)
        : [...withoutAny, value]
    })
  }

  // ===== REST API에 그대로 연결할 수 있는 개인 조건 DTO 구성 =====
  const createPreferencePayload = ({
    useUnrestrictedDefaults = false,
    submissionReason = 'SUBMITTED',
  } = {}) => {
    const hasBudget = !budgetUnrestricted && budgetInput.trim() !== ''
    const hasAvoidAnswer = avoidFoods.length > 0

    return {
      participantId: participant.id,
      excludedFoods: hasAvoidAnswer
        ? avoidFoods.filter((item) => item !== '없어요')
        : [],
      excludedFoodsUnrestricted: useUnrestrictedDefaults && !hasAvoidAnswer,
      budgetLimit: hasBudget ? Number(budgetInput) : null,
      budgetUnrestricted: budgetUnrestricted || (useUnrestrictedDefaults && !hasBudget),
      myDataConsent: myDataConsent === 'AGREED',
      myDataConsentStatus: myDataConsent,
      parkingPreference,
      moodPreferences: moods.length > 0 ? moods : ['상관없어요'],
      submissionReason,
    }
  }

  // ===== 필수값 확인 후 누락 항목이 있으면 3가지 처리 선택창 표시 =====
  const handleSubmit = () => {
    const budget = Number(budgetInput)
    if (!budgetUnrestricted && budgetInput.trim() !== '' && (!Number.isInteger(budget) || budget <= 0)) {
      showFieldError('budget', '예산은 1원 이상의 정수로 입력해주세요.')
      return
    }
    if (!myDataConsent) {
      showFieldError('myDataConsent', '마이데이터 활용 동의 여부를 선택해주세요.')
      return
    }

    setValidationField('')
    setValidationMessage('')
    if (avoidFoods.length === 0 || (!budgetUnrestricted && budgetInput.trim() === '')) {
      setShowMissingChoice(true)
      return
    }
    onSubmit(createPreferencePayload())
  }

  const handleUseUnrestrictedDefaults = () => {
    setShowMissingChoice(false)
    onSubmit(createPreferencePayload({ useUnrestrictedDefaults: true }))
  }

  // ===== 개인 옵션 시간이 끝나면 입력된 값은 유지하고 누락값만 제한 없음으로 제출 =====
  const handlePreferenceExpired = () => {
    onSubmit(createPreferencePayload({
      useUnrestrictedDefaults: true,
      submissionReason: 'TIMEOUT',
    }))
  }

  // ===== 누락 안내창에서 다시 선택하면 가장 위의 미입력 필수 항목으로 이동 =====
  const handleChooseMissingAgain = () => {
    setShowMissingChoice(false)
    if (avoidFoods.length === 0) {
      showFieldError('avoidFoods', '피하고 싶은 음식 또는 없어요를 선택해주세요.')
      return
    }
    if (!budgetUnrestricted && budgetInput.trim() === '') {
      showFieldError('budget', '1인당 예산 상한을 입력하거나 상관없어요를 선택해주세요.')
    }
  }

  if (!open) return null

  return (
    <div
      className={`ui-layer participant-preference-layer${isCollapsed ? ' is-collapsed' : ''}`}
      style={{
        '--participant-visible-height': `${viewportMetrics.visibleHeight}px`,
        '--participant-keyboard-inset': `${viewportMetrics.bottomInset}px`,
      }}
      role="presentation"
    >
      <button
        className="ui-backdrop"
        type="button"
        aria-label="개인 조건 입력 닫기"
        onClick={closePreferencePage}
      />

      <section
        ref={sheetRef}
        className={`ui-sheet participant-preference-sheet${isCollapsed ? ' is-collapsed' : ''}${isDragging ? ' is-dragging' : ''}`}
        style={dragOffset == null ? undefined : { transform: `translateY(${dragOffset}px)` }}
        role="dialog"
        aria-modal={!isCollapsed}
        aria-labelledby="participant-preference-title"
      >
        {/* 손잡이를 아래로 밀면 접히고, 접힌 손잡이를 위로 밀면 다시 펼쳐집니다. */}
        <button
          className="participant-preference-handle"
          type="button"
          aria-label={isCollapsed ? '내 조건 입력 펼치기' : '내 조건 입력 접기'}
          aria-expanded={!isCollapsed}
          onClick={toggleCollapsed}
          onMouseDown={startSheetMouseDrag}
          onPointerDown={startSheetPointerDrag}
          onPointerMove={moveSheetPointerDrag}
          onPointerUp={finishSheetPointerDrag}
          onPointerCancel={cancelSheetDrag}
        >
          <span />
        </button>

        <header className="ui-sheet__header participant-preference-header" aria-hidden={isCollapsed}>
          <button className="ui-sheet__back" type="button" aria-label="개인 조건 입력 닫기" onClick={closePreferencePage}>‹</button>
          <img src={momeokjiIcon} alt="" />
          <h2 id="participant-preference-title">내 조건 입력</h2>
          <CountdownTimer deadlineAt={deadlineAt} label="선택 마감" onExpire={handlePreferenceExpired} />
        </header>

        <div className="ui-sheet__body participant-preference-body" aria-hidden={isCollapsed}>
          <div className="participant-preference-intro">
            <strong>{participant.name}님의 조건을 알려주세요</strong>
            <p>{meetingSummary}</p>
          </div>

          <section
            className={`participant-preference-section${validationField === 'avoidFoods' ? ' is-invalid' : ''}`}
            ref={(element) => { sectionRefs.current.avoidFoods = element }}
          >
            <div className="participant-preference-label">
              <strong>피하고 싶은 음식</strong>
              <span>필수</span>
            </div>
            <p>알레르기나 못 먹는 음식은 후보에서 제외돼요.</p>
            <PreferenceChips
              label="피하고 싶은 음식"
              options={selectableAvoidOptions}
              selected={avoidFoods}
              onToggle={toggleAvoidFood}
            />
            <AddableMenuInput
              value={avoidInput}
              placeholder="알레르기나 못 먹는 음식 입력"
              ariaLabel="피하고 싶은 음식 직접 입력"
              onChange={setAvoidInput}
              onAdd={addCustomAvoidFood}
            />
            {validationField === 'avoidFoods' && (
              <p className="ui-alert ui-alert--error" role="alert">{validationMessage}</p>
            )}
          </section>

          <section
            className={`participant-preference-section${validationField === 'budget' ? ' is-invalid' : ''}`}
            ref={(element) => { sectionRefs.current.budget = element }}
          >
            <div className="participant-preference-label">
              <strong>1인당 예산 상한</strong>
              <span>필수</span>
            </div>
            <p>추천에 사용할 최대 금액을 선택하거나 직접 입력해주세요.</p>
            {/* ===== 자주 쓰는 예산은 한 번에 선택하고 상관없음도 명시적으로 전송 ===== */}
            <PreferenceChips
              label="예산 빠른 선택"
              options={BUDGET_OPTIONS}
              selected={budgetUnrestricted ? 'UNRESTRICTED' : budgetInput}
              onToggle={(value) => {
                const isUnrestricted = value === 'UNRESTRICTED'
                setBudgetUnrestricted(isUnrestricted)
                setBudgetInput(isUnrestricted ? '' : value)
                clearFieldError('budget')
              }}
              single
            />
            <div className="participant-preference-budget">
              <input
                className="ui-input"
                type="text"
                inputMode="numeric"
                placeholder="예: 20,000"
                aria-label="1인당 예산 상한"
                aria-invalid={validationField === 'budget'}
                disabled={budgetUnrestricted}
                value={formatBudgetInput(budgetInput)}
                onChange={(event) => {
                  setBudgetUnrestricted(false)
                  setBudgetInput(event.target.value.replace(/\D/g, ''))
                  clearFieldError('budget')
                }}
              />
              <span>원</span>
            </div>
            {validationField === 'budget' && (
              <p className="ui-alert ui-alert--error" role="alert">{validationMessage}</p>
            )}
          </section>

          <section className="participant-preference-section participant-preference-section--optional">
            <div className="participant-preference-label">
              <strong>주차 여부</strong>
              <em>선택</em>
            </div>
            <PreferenceChips
              label="주차 여부"
              options={PARKING_OPTIONS}
              selected={parkingPreference}
              onToggle={setParkingPreference}
              single
            />
          </section>

          <section className="participant-preference-section participant-preference-section--optional">
            <div className="participant-preference-label">
              <strong>원하는 분위기</strong>
              <em>선택</em>
            </div>
            <PreferenceChips
              label="원하는 분위기"
              options={MOOD_OPTIONS}
              selected={moods}
              onToggle={toggleMood}
            />
          </section>

          {/* ===== 선택 조건을 모두 확인한 뒤 마지막으로 마이데이터 활용 여부 결정 ===== */}
          <section
            className={`participant-preference-section${validationField === 'myDataConsent' ? ' is-invalid' : ''}`}
            ref={(element) => { sectionRefs.current.myDataConsent = element }}
          >
            <div className="participant-preference-label">
              <strong>마이데이터 활용</strong>
              <span>필수</span>
            </div>
            <p>동의 여부를 선택해주세요. 동의하지 않아도 모임에는 참여할 수 있어요.</p>

            {/* ===== 마이데이터 활용 범위를 선택 전에 바로 확인하는 핵심 요약 ===== */}
            <div className="participant-preference-data-summary">
              <dl>
                <div>
                  <dt>사용 데이터</dt>
                  <dd>목업 결제 내역의 음식점 업종·금액·이용 지역</dd>
                </div>
                <div>
                  <dt>이용 목적</dt>
                  <dd>참가자 조건을 반영한 식당 추천</dd>
                </div>
                <div>
                  <dt>보관 기간</dt>
                  <dd>이번 모먹지 추천이 종료될 때까지</dd>
                </div>
                <div>
                  <dt>미동의 시</dt>
                  <dd>마이데이터를 제외한 입력 조건만 추천에 반영</dd>
                </div>
              </dl>
            </div>

            {/* ===== 긴 안내는 필요할 때만 펼쳐 읽는 상세 약관 ===== */}
            <details className="participant-preference-data-terms">
              <summary>마이데이터 활용 안내 자세히 보기</summary>
              <div>
                <p>동의한 참가자의 목업 결제 데이터만 이번 모임의 식당 추천 조건으로 사용합니다.</p>
                <p>데이터는 다른 참가자에게 개별 내역으로 공개되지 않으며, 추천 종료 후에는 더 이상 사용하지 않습니다.</p>
                <p>동의를 거부해도 모임과 투표에 참여할 수 있으며, 직접 입력한 조건만 추천에 반영됩니다.</p>
              </div>
            </details>
            <div
              className="participant-preference-consent"
              role="group"
              aria-label="마이데이터 활용 동의"
              aria-invalid={validationField === 'myDataConsent'}
            >
              <button
                className={myDataConsent === 'AGREED' ? 'is-selected' : ''}
                type="button"
                aria-pressed={myDataConsent === 'AGREED'}
                onClick={() => {
                  setMyDataConsent('AGREED')
                  clearFieldError('myDataConsent')
                }}
              >
                동의
              </button>
              <button
                className={myDataConsent === 'DECLINED' ? 'is-selected' : ''}
                type="button"
                aria-pressed={myDataConsent === 'DECLINED'}
                onClick={() => {
                  setMyDataConsent('DECLINED')
                  clearFieldError('myDataConsent')
                }}
              >
                동의하지 않음
              </button>
            </div>
            {validationField === 'myDataConsent' && (
              <p className="ui-alert ui-alert--error" role="alert">{validationMessage}</p>
            )}
          </section>

        </div>

        <footer className="ui-sheet__footer participant-preference-footer" aria-hidden={isCollapsed}>
          <button
            className="app-button app-button--primary app-button--large"
            type="button"
            onClick={handleSubmit}
          >
            개인 조건 제출
          </button>
        </footer>

        {showMissingChoice && (
          <div className="participant-preference-missing" role="presentation">
            <section role="dialog" aria-modal="true" aria-labelledby="missing-preference-title">
              <h3 id="missing-preference-title">입력하지 않은 항목이 있어요</h3>
              <p>제외 음식 또는 예산을 어떻게 처리할까요?</p>
              <button className="app-button app-button--primary app-button--large" type="button" onClick={handleUseUnrestrictedDefaults}>
                아무거나
              </button>
              <button className="app-button app-button--large" type="button" onClick={handleChooseMissingAgain}>
                다시 고르기
              </button>
              <button className="participant-preference-decline" type="button" onClick={onDecline}>
                참여 안 하기
              </button>
            </section>
          </div>
        )}
      </section>
    </div>
  )
}

export default ParticipantPreferencePage
