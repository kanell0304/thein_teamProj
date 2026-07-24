import { useEffect, useMemo, useRef, useState } from 'react'
import momeokjiIcon from '../assets/icons/momeokji-icon.png'
import CalendarPicker from '../components/momeokji/CalendarPicker'
import NextProgressButton from '../components/momeokji/NextProgressButton'
import ParticipantPicker from '../components/momeokji/ParticipantPicker'
import PlacePicker from '../components/momeokji/PlacePicker'
import TimePicker from '../components/momeokji/TimePicker'
import AddableMenuInput from '../components/momeokji/AddableMenuInput'
import {
  MENU_ANY_OPTION,
  PERSONAL_OPTION_DURATION,
  THEMES,
  TIME_OPTIONS,
  TOTAL_STEPS,
  VOTE_DURATION,
} from '../constants/momeokjiOptions'
import { analyzeConversationKeywords } from '../services/momeokjiService'
import './MomeokjiPage.css'

const KEYWORD_TYPES = ['MENU', 'CATEGORY', 'RESTAURANT']
const KEYWORD_ROW_CONFIG = [
  { type: 'MENU', label: '메뉴' },
  { type: 'CATEGORY', label: '카테고리' },
  { type: 'RESTAURANT', label: '음식점명' },
]
const DRAG_SCROLL_THRESHOLD_PX = 5
const MAX_EXTRACTED_KEYWORDS_PER_TYPE = 10

function createKeywordOption(type, name) {
  return {
    key: `${type}:${name}`,
    type,
    name,
  }
}

function isVisibleKeywordScore(item) {
  return item
    && KEYWORD_TYPES.includes(item.type)
    && typeof item.name === 'string'
    && item.name.trim()
    && Number(item.score) >= 1
}

function formatLocalDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// ===== 현재 시각과 분 단위 차이가 가장 작은 시간 옵션 계산 =====
function getNearestTimeValue(now = new Date()) {
  const currentMinutes = now.getHours() * 60 + now.getMinutes()

  return TIME_OPTIONS.reduce((nearest, option) => {
    const [hour, minute] = option.value.split(':').map(Number)
    const optionMinutes = hour * 60 + minute
    const nearestMinutes = nearest
      .split(':')
      .map(Number)
      .reduce((total, value, index) => total + value * (index === 0 ? 60 : 1), 0)
    const optionDifference = Math.abs(optionMinutes - currentMinutes)
    const nearestDifference = Math.abs(nearestMinutes - currentMinutes)

    // 차이가 같다면 약속 시간 특성상 현재 이후 옵션을 우선합니다.
    return optionDifference < nearestDifference
      || (optionDifference === nearestDifference && optionMinutes >= currentMinutes)
      ? option.value
      : nearest
  }, TIME_OPTIONS[0].value)
}

// ===== PersonalPreferencePage 등 다른 화면에서도 재사용하는 칩 선택 그룹 =====
export function ChipGroup({ label, options, selected, onToggle, single = false }) {
  return (
    <div className="ui-chip-group momeokji-chips" aria-label={label}>
      {options.map((option) => {
        const optionValue = typeof option === 'string' ? option : option.value
        const optionLabel = typeof option === 'string' ? option : option.label
        const isSelected = single ? selected === optionValue : selected.includes(optionValue)
        return (
          <button
            className={`ui-chip${isSelected ? ' is-selected' : ''}`}
            data-option-value={optionValue}
            type="button"
            aria-pressed={isSelected}
            key={optionValue}
            onClick={() => onToggle(optionValue)}
          >
            {optionLabel}
          </button>
        )
      })}
    </div>
  )
}

function KeywordRow({ label, options, selected, onToggle }) {
  const dragStateRef = useRef(null)
  const suppressClickRef = useRef(false)
  const [isDragging, setIsDragging] = useState(false)

  if (options.length === 0) return null

  const handlePointerDown = (event) => {
    if (event.pointerType === 'touch' || event.button !== 0) return

    const row = event.currentTarget
    if (row.scrollWidth <= row.clientWidth) return

    dragStateRef.current = {
      pointerId: event.pointerId,
      captureTarget: event.target,
      startX: event.clientX,
      startScrollLeft: row.scrollLeft,
      isDragging: false,
    }
    event.target.setPointerCapture(event.pointerId)
  }

  const handlePointerMove = (event) => {
    const dragState = dragStateRef.current
    if (!dragState || dragState.pointerId !== event.pointerId) return

    const distance = event.clientX - dragState.startX
    if (!dragState.isDragging) {
      if (Math.abs(distance) < DRAG_SCROLL_THRESHOLD_PX) return
      dragState.isDragging = true
      setIsDragging(true)
    }

    event.preventDefault()
    event.currentTarget.scrollLeft = dragState.startScrollLeft - distance
  }

  const finishPointerDrag = (event) => {
    const dragState = dragStateRef.current
    if (!dragState || dragState.pointerId !== event.pointerId) return

    dragStateRef.current = null
    if (dragState.captureTarget.hasPointerCapture(event.pointerId)) {
      dragState.captureTarget.releasePointerCapture(event.pointerId)
    }
    setIsDragging(false)

    if (dragState.isDragging) {
      suppressClickRef.current = true
      window.setTimeout(() => {
        suppressClickRef.current = false
      }, 0)
    }
  }

  const preventClickAfterDrag = (event) => {
    if (!suppressClickRef.current) return
    suppressClickRef.current = false
    event.preventDefault()
    event.stopPropagation()
  }

  return (
    <div
      className={`momeokji-keyword-row${isDragging ? ' is-dragging' : ''}`}
      role="group"
      aria-label={`${label} 선택`}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={finishPointerDrag}
      onPointerCancel={finishPointerDrag}
      onLostPointerCapture={finishPointerDrag}
      onClickCapture={preventClickAfterDrag}
    >
      {options.map((option) => {
        const isSelected = selected.includes(option.key)
        return (
          <button
            className={`ui-chip${isSelected ? ' is-selected' : ''}`}
            data-option-value={option.name}
            type="button"
            aria-pressed={isSelected}
            key={option.key}
            onClick={() => onToggle(option.key)}
          >
            {option.name}
          </button>
        )
      })}
    </div>
  )
}

// ===== 개인 옵션과 식당 투표 제한시간을 동일한 분 단위 입력으로 관리 =====
function DurationField({ label, description, value, min, max, onChange }) {
  return (
    <label className="momeokji-duration-field">
      <span>
        <strong>{label}</strong>
        <output>{value}분</output>
      </span>
      <small>{description}</small>
      <input
        type="range"
        min={min}
        max={max}
        step="1"
        value={value}
        aria-label={label}
        onChange={(event) => onChange(Number(event.target.value))}
      />
      <em><span>최소 {min}분</span><span>최대 {max}분</span></em>
    </label>
  )
}

function MomeokjiPage({
  open,
  onClose,
  onComplete,
  chatRoomId,
  featureStartedAt,
  participants = [],
  defaultParticipantIds = [],
}) {
  const [step, setStep] = useState(0)
  const [date, setDate] = useState(() => formatLocalDate(new Date()))
  const [time, setTime] = useState(() => getNearestTimeValue())
  const [place, setPlace] = useState(null)
  const [participantIds, setParticipantIds] = useState(() => defaultParticipantIds)
  const [personalOptionDurationMinutes, setPersonalOptionDurationMinutes] = useState(
    PERSONAL_OPTION_DURATION.defaultValue,
  )
  const [voteDurationMinutes, setVoteDurationMinutes] = useState(VOTE_DURATION.defaultValue)
  const [themeCode, setThemeCode] = useState('MEAL')
  const [keywordScores, setKeywordScores] = useState([])
  const [customMenuOptions, setCustomMenuOptions] = useState([])
  const [selectedKeywordKeys, setSelectedKeywordKeys] = useState([])
  const [menuInput, setMenuInput] = useState('')
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [analysisMessage, setAnalysisMessage] = useState('')
  const analyzedRequestKeyRef = useRef(null)

  // ===== 주최자 ID는 UI 상태와 무관하게 최종 참가자 값에 항상 포함 =====
  const effectiveParticipantIds = useMemo(
    () => [...new Set([...defaultParticipantIds, ...participantIds])],
    [defaultParticipantIds, participantIds],
  )
  const analysisParticipantIds = useMemo(
    () => [...effectiveParticipantIds].sort((left, right) => Number(left) - Number(right)),
    [effectiveParticipantIds],
  )
  const analysisRequestKey = featureStartedAt
    ? `${featureStartedAt}|${analysisParticipantIds.join(',')}`
    : null

  useEffect(() => {
    if (!open) return undefined
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [open, onClose])

  useEffect(() => {
    if (
      analyzedRequestKeyRef.current !== null
      && analyzedRequestKeyRef.current !== analysisRequestKey
    ) {
      analyzedRequestKeyRef.current = null
    }

    if (
      !open
      || step !== TOTAL_STEPS - 1
      || !chatRoomId
      || !analysisRequestKey
      || analyzedRequestKeyRef.current === analysisRequestKey
    ) return

    analyzedRequestKeyRef.current = analysisRequestKey
    setIsAnalyzing(true)
    setAnalysisMessage('')
    setKeywordScores([])
    setSelectedKeywordKeys([])

    analyzeConversationKeywords(chatRoomId, featureStartedAt, analysisParticipantIds)
      .then((items) => {
        if (analyzedRequestKeyRef.current !== analysisRequestKey) return
        setKeywordScores(items)
        setAnalysisMessage(items.some(isVisibleKeywordScore)
          ? '대화 내용을 바탕으로 찾은 선택지예요.'
          : '대화에서 선택지를 찾지 못했어요. 아무거나를 선택하거나 메뉴를 직접 추가해주세요.')
      })
      .catch(() => {
        if (analyzedRequestKeyRef.current !== analysisRequestKey) return
        setKeywordScores([])
        setAnalysisMessage('대화 추천 항목을 불러오지 못했어요. 아무거나를 선택하거나 메뉴를 직접 추가해주세요.')
      })
      .finally(() => {
        if (analyzedRequestKeyRef.current === analysisRequestKey) {
          setIsAnalyzing(false)
        }
      })
  }, [
    analysisParticipantIds,
    analysisRequestKey,
    chatRoomId,
    featureStartedAt,
    open,
    step,
  ])

  const selectedParticipantNames = useMemo(
    () => participants
      .filter((person) => effectiveParticipantIds.includes(person.id))
      .map((person) => person.name),
    [effectiveParticipantIds, participants],
  )

  // ===== 순점수 1 이상 대화 키워드를 유형별 최대 10개로 분리 =====
  const extractedKeywordOptions = useMemo(() => {
    const optionsByType = {
      MENU: [],
      CATEGORY: [],
      RESTAURANT: [],
    }
    const seenKeys = new Set()

    keywordScores.forEach((item) => {
      if (!isVisibleKeywordScore(item)) return

      const option = createKeywordOption(item.type, item.name.trim())
      if (
        seenKeys.has(option.key)
        || optionsByType[option.type].length >= MAX_EXTRACTED_KEYWORDS_PER_TYPE
      ) return

      seenKeys.add(option.key)
      optionsByType[option.type].push(option)
    })

    return optionsByType
  }, [keywordScores])

  // ===== 결과가 있는 유형만 행으로 만들어 빈 공간이 생기지 않도록 구성 =====
  const visibleExtractedKeywordRows = useMemo(
    () => KEYWORD_ROW_CONFIG
      .map(({ type, label }) => ({
        type,
        label,
        options: extractedKeywordOptions[type],
      }))
      .filter(({ options }) => options.length > 0),
    [extractedKeywordOptions],
  )

  // ===== 아무거나와 직접 추가 메뉴는 실제 추천 행 바로 다음 줄에 유지 =====
  const customMenuRowOptions = useMemo(() => {
    const options = [
      createKeywordOption('MENU', MENU_ANY_OPTION),
      ...customMenuOptions.map((name) => createKeywordOption('MENU', name)),
    ]
    return [...new Map(options.map((option) => [option.key, option])).values()]
  }, [customMenuOptions])

  const keywordOptions = useMemo(
    () => [
      ...extractedKeywordOptions.MENU,
      ...extractedKeywordOptions.CATEGORY,
      ...extractedKeywordOptions.RESTAURANT,
      ...customMenuRowOptions,
    ],
    [customMenuRowOptions, extractedKeywordOptions],
  )
  const selectedKeywordNames = useMemo(() => {
    const nameByKey = new Map(keywordOptions.map((option) => [option.key, option.name]))
    return [...new Set(
      selectedKeywordKeys
        .map((key) => nameByKey.get(key))
        .filter(Boolean),
    )]
  }, [keywordOptions, selectedKeywordKeys])

  const toggleArrayValue = (setter, value) => {
    setter((previous) => (
      previous.includes(value)
        ? previous.filter((item) => item !== value)
        : [...previous, value]
    ))
  }

  // ===== 입력 메뉴를 칩으로 추가하고 즉시 선택 상태로 전환 =====
  const addCustomMenu = () => {
    const newMenu = menuInput.trim()
    if (!newMenu) return
    const newMenuKey = createKeywordOption('MENU', newMenu).key

    setCustomMenuOptions((previous) => (
      extractedKeywordOptions.MENU.some((option) => option.name === newMenu)
        || previous.includes(newMenu)
        ? previous
        : [...previous, newMenu]
    ))
    setSelectedKeywordKeys((previous) => (
      previous.includes(newMenuKey) ? previous : [...previous, newMenuKey]
    ))
    setMenuInput('')
  }

  const isStepValid = [
    Boolean(date),
    Boolean(time),
    Boolean(place),
    effectiveParticipantIds.length > 0,
    personalOptionDurationMinutes >= PERSONAL_OPTION_DURATION.min
      && personalOptionDurationMinutes <= PERSONAL_OPTION_DURATION.max
      && voteDurationMinutes >= VOTE_DURATION.min
      && voteDurationMinutes <= VOTE_DURATION.max,
    Boolean(themeCode),
    selectedKeywordKeys.length > 0,
  ][step]

  const handleNext = () => {
    if (!isStepValid) return
    if (step < TOTAL_STEPS - 1) {
      if (
        step === TOTAL_STEPS - 2
        && analysisRequestKey
        && analyzedRequestKeyRef.current !== analysisRequestKey
      ) {
        setIsAnalyzing(true)
        setAnalysisMessage('')
      }
      setStep((previous) => previous + 1)
      return
    }

    onComplete({
      date,
      time,
      timeZone: 'Asia/Seoul',
      timeLabel: TIME_OPTIONS.find((option) => option.value === time)?.label ?? time,
      place,
      participantIds: effectiveParticipantIds,
      participantNames: selectedParticipantNames,
      personalOptionDurationMinutes,
      voteDurationMinutes,
      themeCode,
      themeLabel: THEMES.find((option) => option.value === themeCode)?.label ?? themeCode,
      menus: selectedKeywordNames,
      // 개인 취향은 주최자를 포함한 각 참가자의 개인 조건 화면에서만 수집합니다.
      avoidFoods: [],
      moods: [],
    })
    setStep(0)
    onClose()
  }

  const renderStep = () => {
    switch (step) {
      case 0:
        return (
          <div className="momeokji-step">
            <h3>언제 만날까요?</h3>
            <p className="momeokji-description">월별 달력에서 모임 날짜를 선택해주세요.</p>
            <CalendarPicker value={date} onChange={setDate} />
          </div>
        )
      case 1:
        return (
          <div className="momeokji-step">
            <h3>몇 시에 만날까요?</h3>
            <p className="momeokji-description">원하는 시간대를 선택해주세요.</p>
            {/* ===== 24시간 내부 스크롤 선택 ===== */}
            <TimePicker options={TIME_OPTIONS} value={time} onChange={setTime} />
          </div>
        )
      case 2:
        return (
          <div className="momeokji-step">
            <h3>어디에서 만날까요?</h3>
            <p className="momeokji-description">장소를 검색하고 지도에서 선택해주세요.</p>
            <PlacePicker value={place} onChange={setPlace} />
          </div>
        )
      case 3:
        return (
          <div className="momeokji-step">
            <h3>누가 참가하나요?</h3>
            <p className="momeokji-description">선택한 참가자에게만 최종 공지가 보여요.</p>
            <ParticipantPicker
              people={participants}
              selectedIds={effectiveParticipantIds}
              requiredIds={defaultParticipantIds}
              onChange={setParticipantIds}
            />
          </div>
        )
      case 4:
        return (
          <div className="momeokji-step">
            <h3>참여 시간을 정해주세요</h3>
            <p className="momeokji-description">개인 조건 입력과 식당 투표의 제한 시간을 각각 설정해요.</p>
            <div className="momeokji-duration-list">
              <DurationField
                label="개인 조건 입력 시간"
                description="참가자가 음식 취향과 개인 조건을 입력할 수 있는 시간이에요."
                value={personalOptionDurationMinutes}
                min={PERSONAL_OPTION_DURATION.min}
                max={PERSONAL_OPTION_DURATION.max}
                onChange={setPersonalOptionDurationMinutes}
              />
              <DurationField
                label="식당 투표 시간"
                description="추천 식당이 공개된 후 투표에 참여할 수 있는 시간이에요."
                value={voteDurationMinutes}
                min={VOTE_DURATION.min}
                max={VOTE_DURATION.max}
                onChange={setVoteDurationMinutes}
              />
            </div>
          </div>
        )
      case 5:
        return (
          <div className="momeokji-step">
            <h3>모임의 주제가 무엇인가요?</h3>
            <p className="momeokji-description">선택한 테마 코드는 AI 메뉴 추천 조건으로 전달돼요.</p>
            <ChipGroup label="모임 테마" options={THEMES} selected={themeCode} onToggle={setThemeCode} single />
          </div>
        )
      case 6:
        return (
          <div className="momeokji-step momeokji-keyword-step">
            <div className="momeokji-step-title">
              <h3>오늘 대화엔 이런 메뉴가 나왔어요</h3>
              <span>필수</span>
            </div>
            <p className="momeokji-description" aria-live="polite">
              {isAnalyzing ? '대화에서 메뉴와 음식점을 찾고 있어요…' : analysisMessage}
            </p>
            {!isAnalyzing && (
              <div className="momeokji-keyword-sectors">
                {visibleExtractedKeywordRows.map(({ type, label, options }) => (
                  <KeywordRow
                    key={type}
                    label={label}
                    options={options}
                    selected={selectedKeywordKeys}
                    onToggle={(value) => toggleArrayValue(setSelectedKeywordKeys, value)}
                  />
                ))}
                <KeywordRow
                  label="직접 추가 메뉴"
                  options={customMenuRowOptions}
                  selected={selectedKeywordKeys}
                  onToggle={(value) => toggleArrayValue(setSelectedKeywordKeys, value)}
                />
              </div>
            )}
          </div>
        )
      default:
        return null
    }
  }

  if (!open) return null

  return (
    <div className="ui-layer momeokji-layer" role="presentation">
      <button className="ui-backdrop" type="button" aria-label="모먹지 닫기" onClick={onClose} />
      <section className="ui-sheet momeokji-sheet" role="dialog" aria-modal="true" aria-labelledby="momeokji-title">
        <header className="ui-sheet__header momeokji-sheet__header">
          {step > 0 && (
            <button className="ui-sheet__back" type="button" aria-label="이전 단계" onClick={() => setStep((previous) => previous - 1)}>‹</button>
          )}
          <img src={momeokjiIcon} alt="" />
          <h2 id="momeokji-title">오늘 모 먹지?</h2>
          <span className="momeokji-step-count">{step + 1}/{TOTAL_STEPS}</span>
        </header>
        <div className="ui-sheet__body">{renderStep()}</div>
        {step === TOTAL_STEPS - 1 && (
          <div className="momeokji-menu-input-footer">
            <AddableMenuInput
              value={menuInput}
              onChange={setMenuInput}
              onAdd={addCustomMenu}
            />
          </div>
        )}
        <NextProgressButton
          currentStep={step + 1}
          totalSteps={TOTAL_STEPS}
          label={step === TOTAL_STEPS - 1 ? '완료' : '다음'}
          onClick={handleNext}
          disabled={!isStepValid}
        />
      </section>
    </div>
  )
}

export default MomeokjiPage
