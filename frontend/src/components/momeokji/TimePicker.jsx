/**
 * 24시간 순환형 시간 선택 목록.
 * 같은 목록을 세 번 배치하고 양끝에 가까워지면 가운데 목록으로 이동해 끊김 없이 순환합니다.
 */
import { useEffect, useMemo, useRef } from 'react'
import './TimePicker.css'

const TIME_ITEM_HEIGHT = 44

function TimePicker({ options, value, onChange }) {
  const listRef = useRef(null)
  const scrollFrameRef = useRef(null)
  const internalChangeRef = useRef(false)
  const repeatedOptions = useMemo(
    () => Array.from({ length: 3 }, (_, cycle) => (
      options.map((option) => ({ option, cycle }))
    )).flat(),
    [options],
  )

  // ===== 최초 진입 또는 외부 값 변경 시 가운데 순환 목록의 선택 시간으로 이동 =====
  useEffect(() => {
    const list = listRef.current
    if (!list || options.length === 0) return
    if (internalChangeRef.current) {
      internalChangeRef.current = false
      return
    }

    const selectedIndex = Math.max(0, options.findIndex((option) => option.value === value))
    list.scrollTop = (options.length + selectedIndex) * TIME_ITEM_HEIGHT
  }, [options, value])

  useEffect(() => () => {
    if (scrollFrameRef.current) cancelAnimationFrame(scrollFrameRef.current)
  }, [])

  const handleScroll = () => {
    const list = listRef.current
    if (!list || options.length === 0) return

    if (scrollFrameRef.current) cancelAnimationFrame(scrollFrameRef.current)
    scrollFrameRef.current = requestAnimationFrame(() => {
      const cycleHeight = options.length * TIME_ITEM_HEIGHT
      let nextScrollTop = list.scrollTop

      if (nextScrollTop < cycleHeight / 2) {
        nextScrollTop += cycleHeight
        list.scrollTop = nextScrollTop
      } else if (nextScrollTop > cycleHeight * 2.5) {
        nextScrollTop -= cycleHeight
        list.scrollTop = nextScrollTop
      }

      const normalizedIndex = ((Math.round(nextScrollTop / TIME_ITEM_HEIGHT) % options.length)
        + options.length) % options.length
      const nextOption = options[normalizedIndex]
      if (nextOption?.value !== value) {
        internalChangeRef.current = true
        onChange(nextOption.value)
      }
    })
  }

  const selectOption = (option, repeatedIndex) => {
    const list = listRef.current
    internalChangeRef.current = true
    onChange(option.value)
    list?.scrollTo({ top: repeatedIndex * TIME_ITEM_HEIGHT, behavior: 'smooth' })
  }

  return (
    <div className="time-picker">
      {/* ===== 세 번 반복한 목록을 내부에서만 스크롤해 23시와 00시를 연결 ===== */}
      <div
        className="time-picker__list"
        ref={listRef}
        role="listbox"
        aria-label="시간대"
        onScroll={handleScroll}
      >
        {repeatedOptions.map(({ option, cycle }, repeatedIndex) => {
          const isSelected = option.value === value
          const isAccessibleCycle = cycle === 1

          return (
            <button
              className={`time-picker__option${isSelected ? ' is-selected' : ''}`}
              type="button"
              role="option"
              aria-selected={isSelected}
              aria-hidden={!isAccessibleCycle}
              tabIndex={isAccessibleCycle ? 0 : -1}
              data-time-value={option.value}
              key={`${cycle}-${option.value}`}
              onClick={() => selectOption(option, repeatedIndex)}
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
