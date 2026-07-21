import { useEffect, useRef, useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import loginLogo from '../assets/momeokji-login-logo.png'
import useAuth from '../hooks/useAuth'
import './LoginPage.css'

function KakaoCallbackPage() {
  const { isAuthenticated, loginWithKakaoCode } = useAuth()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const requestedRef = useRef(false)
  const [errorMessage, setErrorMessage] = useState('')
  const code = searchParams.get('code')

  // ===== 카카오 인가 코드를 한 번만 백엔드에 전달 =====
  useEffect(() => {
    if (!code || requestedRef.current || isAuthenticated) return
    requestedRef.current = true

    loginWithKakaoCode(code)
      .then(() => navigate('/chats', { replace: true }))
      .catch((error) => setErrorMessage(error.message))
  }, [code, isAuthenticated, loginWithKakaoCode, navigate])

  if (isAuthenticated) return <Navigate to="/chats" replace />

  return (
    <main className="login-page">
      <section className="login-card" aria-live="polite">
        {/* ===== 로그인 화면과 동일한 브랜드 로고 사용 ===== */}
        <img className="login-brand-logo" src={loginLogo} alt="오늘 모 먹지?" />
        <h1>카카오 로그인</h1>
        {errorMessage || !code ? (
          <>
            <p className="login-error">{errorMessage || '카카오 인가 코드가 없습니다.'}</p>
            <button className="app-button app-button--large" type="button" onClick={() => navigate('/login', { replace: true })}>
              로그인으로 돌아가기
            </button>
          </>
        ) : (
          <p className="login-description">로그인 정보를 확인하고 있어요.</p>
        )}
      </section>
    </main>
  )
}

export default KakaoCallbackPage
