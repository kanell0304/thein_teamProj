import { useMemo, useState } from 'react'
import './CalendarPicker.css'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function toDateValue(year, month, day) {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

function CalendarPicker({ value, onChange }) {
  const initial = value ? new Date(`${value}T00:00:00`) : new Date()
  const [viewDate, setViewDate] = useState(() => new Date(initial.getFullYear(), initial.getMonth(), 1))
  const days = useMemo(() => {
    const year = viewDate.getFullYear()
    const month = viewDate.getMonth()
    const firstWeekday = new Date(year, month, 1).getDay()
    const lastDay = new Date(year, month + 1, 0).getDate()
    return [
      ...Array.from({ length: firstWeekday }, () => null),
      ...Array.from({ length: lastDay }, (_, index) => index + 1),
    ]
  }, [viewDate])

  const moveMonth = (amount) => {
    setViewDate((previous) => new Date(previous.getFullYear(), previous.getMonth() + amount, 1))
  }

  return (
    <div className="calendar-picker">
      <div className="calendar-picker__header">
        <button type="button" aria-label="이전 달" onClick={() => moveMonth(-1)}>‹</button>
        <strong>{viewDate.getFullYear()}년 {viewDate.getMonth() + 1}월</strong>
        <button type="button" aria-label="다음 달" onClick={() => moveMonth(1)}>›</button>
      </div>
      <div className="calendar-picker__grid calendar-picker__weekdays">
        {WEEKDAYS.map((weekday) => <span key={weekday}>{weekday}</span>)}
      </div>
      <div className="calendar-picker__grid">
        {days.map((day, index) => day ? (
          <button
            className={value === toDateValue(viewDate.getFullYear(), viewDate.getMonth(), day) ? 'is-selected' : ''}
            type="button"
            key={`${viewDate.getMonth()}-${day}`}
            aria-label={`${viewDate.getMonth() + 1}월 ${day}일`}
            onClick={() => onChange(toDateValue(viewDate.getFullYear(), viewDate.getMonth(), day))}
          >{day}</button>
        ) : <span key={`empty-${index}`} />)}
      </div>
    </div>
  )
}

export default CalendarPicker
