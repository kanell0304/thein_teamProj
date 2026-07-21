import { useEffect, useMemo, useState } from 'react'
import momeokjiIcon from '../assets/icons/momeokji-icon.png'
import CalendarPicker from '../components/momeokji/CalendarPicker'
import NextProgressButton from '../components/momeokji/NextProgressButton'
import ParticipantPicker from '../components/momeokji/ParticipantPicker'
import PlacePicker from '../components/momeokji/PlacePicker'
import TimePicker from '../components/momeokji/TimePicker'
import AddableMenuInput from '../components/momeokji/AddableMenuInput'
import {
  AVOID_OPTIONS,
  MENU_ANY_OPTION,
  MOOD_OPTIONS,
  PERSONAL_OPTION_DURATION,
  THEMES,
  TIME_OPTIONS,
  TOTAL_STEPS,
  VOTE_DURATION,
} from '../constants/momeokjiOptions'
import { analyzeConversationMenus } from '../services/momeokjiService'
import './MomeokjiPage.css'

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

function ChipGroup({ label, options, selected, onToggle, single = false }) {
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
  messages = [],
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
  const [menuOptions, setMenuOptions] = useState([])
  const [customMenuOptions, setCustomMenuOptions] = useState([])
  const [menus, setMenus] = useState([])
  const [menuInput, setMenuInput] = useState('')
  const [customAvoidOptions, setCustomAvoidOptions] = useState([])
  const [avoidFoods, setAvoidFoods] = useState([])
  const [avoidInput, setAvoidInput] = useState('')
  const [customMoodOptions, setCustomMoodOptions] = useState([])
  const [moods, setMoods] = useState([])
  const [moodInput, setMoodInput] = useState('')
  const [isAnalyzing, setIsAnalyzing] = useState(false)

  useEffect(() => {
    if (!open) return undefined
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [open, onClose])

  useEffect(() => {
    if (!open) return undefined
    let isActive = true
    Promise.resolve()
      .then(() => {
        if (isActive) setIsAnalyzing(true)
        return analyzeConversationMenus(messages, {
          themeCode,
          meetingDate: date,
          meetingTime: time,
          timeZone: 'Asia/Seoul',
          place,
        })
      })
      .then((items) => {
        if (isActive) setMenuOptions(items)
      })
      .catch(() => {
        if (isActive) setMenuOptions([])
      })
      .finally(() => {
        if (isActive) setIsAnalyzing(false)
      })
    return () => {
      isActive = false
    }
  }, [date, messages, open, place, themeCode, time])

  // ===== 주최자 ID는 UI 상태와 무관하게 최종 참가자 값에 항상 포함 =====
  const effectiveParticipantIds = useMemo(
    () => [...new Set([...defaultParticipantIds, ...participantIds])],
    [defaultParticipantIds, participantIds],
  )

  const selectedParticipantNames = useMemo(
    () => participants
      .filter((person) => effectiveParticipantIds.includes(person.id))
      .map((person) => person.name),
    [effectiveParticipantIds, participants],
  )

  // ===== AI 추출 메뉴와 직접 추가 메뉴를 중복 없이 하나의 선택 목록으로 구성 =====
  const selectableMenuOptions = useMemo(
    () => [...new Set([...menuOptions, ...customMenuOptions, MENU_ANY_OPTION])],
    [customMenuOptions, menuOptions],
  )

  // ===== 기본 기피 음식과 직접 추가한 항목을 중복 없이 하나의 선택 목록으로 구성 =====
  const selectableAvoidOptions = useMemo(
    () => [...new Set([...AVOID_OPTIONS, ...customAvoidOptions])],
    [customAvoidOptions],
  )

  // ===== 기본 분위기와 직접 추가한 항목을 중복 없이 하나의 선택 목록으로 구성 =====
  const selectableMoodOptions = useMemo(
    () => [...new Set([...MOOD_OPTIONS, ...customMoodOptions])],
    [customMoodOptions],
  )

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

    setCustomMenuOptions((previous) => (
      menuOptions.includes(newMenu) || previous.includes(newMenu)
        ? previous
        : [...previous, newMenu]
    ))
    setMenus((previous) => (
      previous.includes(newMenu) ? previous : [...previous, newMenu]
    ))
    setMenuInput('')
  }

  // ===== 직접 입력한 기피 음식을 칩으로 추가하고 즉시 다중 선택 상태로 전환 =====
  const addCustomAvoidFood = () => {
    const newAvoidFood = avoidInput.trim()
    if (!newAvoidFood) return

    setCustomAvoidOptions((previous) => (
      AVOID_OPTIONS.includes(newAvoidFood) || previous.includes(newAvoidFood)
        ? previous
        : [...previous, newAvoidFood]
    ))
    setAvoidFoods((previous) => (
      previous.includes(newAvoidFood) ? previous : [...previous, newAvoidFood]
    ))
    setAvoidInput('')
  }

  // ===== 직접 입력한 분위기를 칩으로 추가하고 즉시 다중 선택 상태로 전환 =====
  const addCustomMood = () => {
    const newMood = moodInput.trim()
    if (!newMood) return

    setCustomMoodOptions((previous) => (
      MOOD_OPTIONS.includes(newMood) || previous.includes(newMood)
        ? previous
        : [...previous, newMood]
    ))
    setMoods((previous) => (
      previous.includes(newMood) ? previous : [...previous, newMood]
    ))
    setMoodInput('')
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
    menus.length > 0,
    true,
    true,
  ][step]

  const handleNext = () => {
    if (!isStepValid) return
    if (step < TOTAL_STEPS - 1) {
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
      menus,
      avoidFoods,
      moods,
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
          <div className="momeokji-step">
            <div className="momeokji-step-title">
              <h3>오늘 대화엔 이런 메뉴가 나왔어요</h3>
              <span>필수</span>
            </div>
            <p className="momeokji-description">
              {isAnalyzing ? '대화에서 메뉴를 찾고 있어요…' : '대화 내용을 분석한 추천 메뉴예요.'}
            </p>
            <ChipGroup
              label="AI 추천 메뉴"
              options={selectableMenuOptions}
              selected={menus}
              onToggle={(value) => toggleArrayValue(setMenus, value)}
            />
            <AddableMenuInput
              value={menuInput}
              onChange={setMenuInput}
              onAdd={addCustomMenu}
            />
          </div>
        )
      case 7:
        return (
          <div className="momeokji-step">
            <h3>피하고 싶은 음식이 있나요?</h3>
            <p className="momeokji-description">알레르기나 못 먹는 음식을 알려주세요.</p>
            <ChipGroup label="피하고 싶은 음식" options={selectableAvoidOptions} selected={avoidFoods} onToggle={(value) => toggleArrayValue(setAvoidFoods, value)} />
            <AddableMenuInput
              value={avoidInput}
              placeholder="알레르기나 못 먹는 음식 입력"
              ariaLabel="피하고 싶은 음식 직접 입력"
              onChange={setAvoidInput}
              onAdd={addCustomAvoidFood}
            />
          </div>
        )
      default:
        return (
          <div className="momeokji-step">
            <h3>원하는 분위기를 골라주세요</h3>
            <ChipGroup label="원하는 분위기" options={selectableMoodOptions} selected={moods} onToggle={(value) => toggleArrayValue(setMoods, value)} />
            <AddableMenuInput
              value={moodInput}
              placeholder="원하는 분위기 직접 입력"
              ariaLabel="원하는 분위기 직접 입력"
              onChange={setMoodInput}
              onAdd={addCustomMood}
            />
          </div>
        )
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
