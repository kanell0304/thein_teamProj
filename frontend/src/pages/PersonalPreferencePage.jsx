import { useState } from 'react'
import momeokjiIcon from '../assets/icons/momeokji-icon.png'
import NextProgressButton from '../components/momeokji/NextProgressButton'
import AddableMenuInput from '../components/momeokji/AddableMenuInput'
import { ChipGroup } from './MomeokjiPage'
import {
  AVOID_OPTIONS,
  CATEGORY_OPTIONS,
  MOOD_OPTIONS,
  WALK_MINUTES_OPTIONS,
} from '../constants/momeokjiOptions'
import './MomeokjiPage.css'

// ===== 초대받은 참여자 각자가 제출하는 개인 선호 입력 화면 =====
function PersonalPreferencePage({ open, onClose, onSubmit, isSubmitting = false }) {
  const [walkMinutes, setWalkMinutes] = useState(WALK_MINUTES_OPTIONS[1])
  const [preferredCategories, setPreferredCategories] = useState([])
  const [budgetLimit, setBudgetLimit] = useState('')
  const [parkingNeeded, setParkingNeeded] = useState(false)
  const [excludedFoods, setExcludedFoods] = useState([])
  const [customAvoidOptions, setCustomAvoidOptions] = useState([])
  const [avoidInput, setAvoidInput] = useState('')
  const [atmosphere, setAtmosphere] = useState(null)

  const toggleArrayValue = (setter, value) => {
    setter((previous) => (
      previous.includes(value)
        ? previous.filter((item) => item !== value)
        : [...previous, value]
    ))
  }

  const selectableAvoidOptions = [...new Set([...AVOID_OPTIONS, ...customAvoidOptions])]

  const addCustomAvoidFood = () => {
    const newAvoidFood = avoidInput.trim()
    if (!newAvoidFood) return

    setCustomAvoidOptions((previous) => (
      AVOID_OPTIONS.includes(newAvoidFood) || previous.includes(newAvoidFood)
        ? previous
        : [...previous, newAvoidFood]
    ))
    setExcludedFoods((previous) => (
      previous.includes(newAvoidFood) ? previous : [...previous, newAvoidFood]
    ))
    setAvoidInput('')
  }

  const isValid = preferredCategories.length > 0

  const handleSubmit = () => {
    if (!isValid || isSubmitting) return
    onSubmit({
      walkMinutes,
      preferredCategories,
      budgetLimit: budgetLimit ? Number(budgetLimit) : null,
      parkingNeeded,
      excludedFoods,
      atmosphere,
    })
  }

  if (!open) return null

  return (
    <div className="momeokji-layer" role="presentation">
      <button className="momeokji-backdrop" type="button" aria-label="개인 선호 입력 닫기" onClick={onClose} />
      <section className="momeokji-sheet" role="dialog" aria-modal="true" aria-labelledby="personal-preference-title">
        <header className="momeokji-sheet__header">
          <img src={momeokjiIcon} alt="" />
          <h2 id="personal-preference-title">나의 선호를 알려주세요</h2>
        </header>
        <div className="momeokji-sheet__body">
          <div className="momeokji-step">
            <h3>어떤 음식을 좋아하나요?</h3>
            <p className="momeokji-description">선호하는 음식 카테고리를 하나 이상 선택해주세요.</p>
            <ChipGroup
              label="선호 카테고리"
              options={CATEGORY_OPTIONS}
              selected={preferredCategories}
              onToggle={(value) => toggleArrayValue(setPreferredCategories, value)}
            />
          </div>

          <div className="momeokji-step">
            <h3>피하고 싶은 음식이 있나요?</h3>
            <ChipGroup
              label="피하고 싶은 음식"
              options={selectableAvoidOptions}
              selected={excludedFoods}
              onToggle={(value) => toggleArrayValue(setExcludedFoods, value)}
            />
            <AddableMenuInput
              value={avoidInput}
              placeholder="알레르기나 못 먹는 음식 입력"
              ariaLabel="피하고 싶은 음식 직접 입력"
              onChange={setAvoidInput}
              onAdd={addCustomAvoidFood}
            />
          </div>

          <div className="momeokji-step">
            <h3>원하는 분위기가 있나요?</h3>
            <ChipGroup
              label="원하는 분위기"
              options={MOOD_OPTIONS}
              selected={atmosphere}
              onToggle={setAtmosphere}
              single
            />
          </div>

          <div className="momeokji-step">
            <h3>도보로 몇 분까지 괜찮나요?</h3>
            <ChipGroup
              label="도보 이동 시간"
              options={WALK_MINUTES_OPTIONS.map((minutes) => ({ value: minutes, label: `${minutes}분` }))}
              selected={walkMinutes}
              onToggle={setWalkMinutes}
              single
            />
          </div>

          <div className="momeokji-step">
            <h3>1인당 예산과 주차</h3>
            <input
              type="number"
              min="0"
              placeholder="1인당 예산(원), 상관없으면 비워두세요"
              aria-label="1인당 예산"
              value={budgetLimit}
              onChange={(event) => setBudgetLimit(event.target.value)}
            />
            <label>
              <input
                type="checkbox"
                checked={parkingNeeded}
                onChange={(event) => setParkingNeeded(event.target.checked)}
              />
              주차가 필요해요
            </label>
          </div>
        </div>
        <NextProgressButton
          currentStep={1}
          totalSteps={1}
          label={isSubmitting ? '제출 중...' : '제출하기'}
          onClick={handleSubmit}
          disabled={!isValid || isSubmitting}
        />
      </section>
    </div>
  )
}

export default PersonalPreferencePage
