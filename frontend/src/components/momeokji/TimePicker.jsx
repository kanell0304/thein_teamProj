/**
 * 24시간 시간 선택 목록.
 * 바텀시트 전체가 움직이지 않고 이 컴포넌트 내부만 스크롤됩니다.
 */
import { useEffect, useRef } from 'react'
import './TimePicker.css'

function TimePicker({ options, value, onChange }) {
  const listRef = useRef(null)

  // ===== 선택 시간을 목록 가운데로 배치 =====
  useEffect(() => {
    const list = listRef.current
    if (!list) return

    const selectedButton = [...list.querySelectorAll('[data-time-value]')]
      .find((button) => button.dataset.timeValue === value)
    if (!selectedButton) return

    list.scrollTop = selectedButton.offsetTop
      - ((list.clientHeight - selectedButton.offsetHeight) / 2)
  }, [options, value])

  return (
    <div className="time-picker">
      {/* ===== 시간 목록: 목록 자체에만 스크롤 적용 ===== */}
      <div
        className="time-picker__list"
        ref={listRef}
        role="listbox"
        aria-label="시간대"
      >
        {options.map((option) => {
          const isSelected = option.value === value

          return (
            <button
              className={`time-picker__option${isSelected ? ' is-selected' : ''}`}
              type="button"
              role="option"
              aria-selected={isSelected}
              data-time-value={option.value}
              key={option.value}
              onClick={() => onChange(option.value)}
            >
              {option.label}
            </button>
          )
        })}
      </div>

      {/* ===== 중앙 선택 가이드 ===== */}
      <span className="time-picker__center-guide" aria-hidden="true" />
    </div>
  )
}

export default TimePicker
