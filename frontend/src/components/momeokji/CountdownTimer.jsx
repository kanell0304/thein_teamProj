import { useEffect, useRef, useState } from 'react'
import './CountdownTimer.css'

function calculateRemainingSeconds(deadlineAt) {
  if (!deadlineAt) return 0
  return Math.max(0, Math.ceil((new Date(deadlineAt).getTime() - Date.now()) / 1000))
}

function formatRemainingTime(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

/** 서버가 내려줄 마감 시각을 기준으로 표시하는 공통 카운트다운. */
function CountdownTimer({ deadlineAt, label = '남은 시간', onExpire }) {
  const [remainingSeconds, setRemainingSeconds] = useState(() => (
    calculateRemainingSeconds(deadlineAt)
  ))
  const onExpireRef = useRef(onExpire)
  const hasExpiredRef = useRef(false)

  useEffect(() => {
    onExpireRef.current = onExpire
  }, [onExpire])

  useEffect(() => {
    hasExpiredRef.current = false

    const updateRemainingTime = () => {
      const nextRemainingSeconds = calculateRemainingSeconds(deadlineAt)
      setRemainingSeconds(nextRemainingSeconds)

      if (deadlineAt && nextRemainingSeconds === 0 && !hasExpiredRef.current) {
        hasExpiredRef.current = true
        onExpireRef.current?.()
      }
    }

    updateRemainingTime()
    if (!deadlineAt) return undefined
    const timerId = window.setInterval(updateRemainingTime, 1000)
    return () => window.clearInterval(timerId)
  }, [deadlineAt])

  if (!deadlineAt) return null

  return (
    <span
      className={`countdown-timer${remainingSeconds <= 60 ? ' is-urgent' : ''}`}
      role="timer"
      aria-label={`${label} ${formatRemainingTime(remainingSeconds)}`}
    >
      <small>{label}</small>
      <strong>{formatRemainingTime(remainingSeconds)}</strong>
    </span>
  )
}

export default CountdownTimer
