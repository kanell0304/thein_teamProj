import { useMemo, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import loginLogo from '../assets/momeokji-login-logo.png'
import useAuth from '../hooks/useAuth'
import './LoginPage.css'

// ===== 카카오 OAuth 인가 화면 주소 생성 =====
function createKakaoAuthorizeUrl() {
  const clientId = import.meta.env.VITE_KAKAO_REST_API_KEY
  if (!clientId) return ''

  const redirectUri = import.meta.env.VITE_KAKAO_LOGIN_REDIRECT_URI
    || `${window.location.origin}/oauth/kakao/callback`
  const query = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    // 콘솔에서 "선택 동의"로 설정한 항목은 scope로 명시해야 동의 화면에 노출됩니다.
    scope: 'profile_nickname,profile_image',
  })
  return `https://kauth.kakao.com/oauth/authorize?${query}`
}

function LoginPage() {
  const { isAuthenticated, isAuthenticating, loginForDevelopment } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [kakaoId, setKakaoId] = useState('local-user-1')
  const [nickname, setNickname] = useState('테스트유저')
  const [errorMessage, setErrorMessage] = useState('')
  const isKakaoLoginEnabled = String(import.meta.env.VITE_KAKAO_LOGIN_ENABLED ?? 'false').toLowerCase() === 'true'
  const kakaoAuthorizeUrl = useMemo(() => createKakaoAuthorizeUrl(), [])
  const destination = location.state?.from?.pathname || '/chats'

  if (isAuthenticated) return <Navigate to={destination} replace />

  // ===== 로컬 개발용 로그인 폼 제출 =====
  const handleDevLogin = async (event) => {
    event.preventDefault()
    setErrorMessage('')

    try {
      await loginForDevelopment({ kakaoId: kakaoId.trim(), nickname: nickname.trim() })
      navigate(destination, { replace: true })
    } catch (error) {
      setErrorMessage(error.message)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        {/* ===== 전달받은 오늘 모 먹지 가로형 브랜드 로고 ===== */}
        <img className="login-brand-logo" src={loginLogo} alt="오늘 모 먹지?" />
        <h1 id="login-title" className="login-visually-hidden">오늘 모 먹지?</h1>
        <p className="login-description">로그인하고 채팅방의 모임과 투표에 참여해보세요.</p>

        {/* ===== 설정을 켠 경우에만 실제 카카오 OAuth 로그인 노출 ===== */}
        {isKakaoLoginEnabled && (
          <>
            <a
              className={`login-kakao-button${kakaoAuthorizeUrl ? '' : ' is-disabled'}`}
              href={kakaoAuthorizeUrl || undefined}
              aria-disabled={!kakaoAuthorizeUrl}
              onClick={(event) => {
                if (!kakaoAuthorizeUrl) event.preventDefault()
              }}
            >
              카카오로 시작하기
            </a>
            {!kakaoAuthorizeUrl && (
              <p className="login-help-text">카카오 로그인 설정을 확인해주세요.</p>
            )}
          </>
        )}

        {/* ===== 백엔드 dev 프로필에서만 사용하는 테스트 로그인 ===== */}
        {import.meta.env.DEV && (
          <>
            {isKakaoLoginEnabled && <div className="login-divider"><span>개발 테스트</span></div>}
            <form className="login-dev-form" onSubmit={handleDevLogin}>
              <label htmlFor="dev-kakao-id">테스트 사용자 ID</label>
              <input
                id="dev-kakao-id"
                value={kakaoId}
                onChange={(event) => setKakaoId(event.target.value)}
                placeholder="예: local-user-1"
                required
              />
              <label htmlFor="dev-nickname">닉네임</label>
              <input
                id="dev-nickname"
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                placeholder="예: 테스트유저"
              />
              {errorMessage && <p className="login-error" role="alert">{errorMessage}</p>}
              <button className="app-button app-button--large app-button--primary" type="submit" disabled={isAuthenticating}>
                {isAuthenticating ? '로그인 중...' : '테스트 로그인'}
              </button>
            </form>
          </>
        )}
      </section>
    </main>
  )
}

export default LoginPage
