import { useNavigate } from 'react-router-dom'
import MainScreen from '../components/layout/MainScreen'
import useAuth from '../hooks/useAuth'
import './SettingsPage.css'

function SettingsPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const nickname = user?.nickname || user?.name || '사용자'

  // ===== 저장된 인증 정보를 삭제하고 로그인 화면으로 이동 =====
  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <MainScreen title="설정">
      {/* ===== 현재 로그인 사용자 요약 ===== */}
      <section className="settings-profile">
        <span className="settings-avatar" aria-hidden="true">{nickname.slice(0, 1)}</span>
        <div>
          <strong>{nickname}</strong>
          <small>오늘 모 먹지? 계정</small>
        </div>
      </section>

      {/* ===== 이후 알림·프로필 설정 기능을 붙일 메뉴 구조 ===== */}
      <section className="settings-menu" aria-label="앱 설정">
        <div className="settings-row">
          <span className="material-symbols-outlined" aria-hidden="true">notifications</span>
          <span>알림</span>
          <small>준비 중</small>
        </div>
        <div className="settings-row">
          <span className="material-symbols-outlined" aria-hidden="true">database</span>
          <span>마이데이터 이용 동의</span>
          <small>모임별 선택</small>
        </div>
      </section>

      <button className="settings-logout" type="button" onClick={handleLogout}>
        로그아웃
      </button>
    </MainScreen>
  )
}

export default SettingsPage
