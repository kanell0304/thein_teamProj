import { useEffect, useState } from 'react'
import './MobileStatusBar.css'

function getCurrentTime() {
  const now = new Date()
  return `${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}`
}

function MobileStatusBar({ tone = 'light' }) {
  const [statusTime, setStatusTime] = useState(getCurrentTime)

  // ===== 모든 모바일 화면의 상태바 시각을 현재 시간으로 유지 =====
  useEffect(() => {
    const timerId = window.setInterval(() => setStatusTime(getCurrentTime()), 1000)
    return () => window.clearInterval(timerId)
  }, [])

  return (
    <div className={`mobile-status-bar mobile-status-bar--${tone}`}>
      <time>{statusTime}</time>
      <div className="mobile-status-icons" aria-label="통신 상태">
        <span className="material-symbols-outlined" aria-hidden="true">signal_cellular_alt</span>
        <span className="material-symbols-outlined" aria-hidden="true">wifi</span>
        <span className="material-symbols-outlined" aria-hidden="true">battery_full</span>
      </div>
    </div>
  )
}

export default MobileStatusBar
