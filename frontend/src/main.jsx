import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.jsx'
import AuthProvider from './contexts/AuthProvider.jsx'
import { initializeKakaoMapProvider } from './services/kakaoMapApi'

// ===== 카카오맵 공급자 등록 =====
// 키 또는 등록 도메인이 올바르지 않으면 장소 선택 화면에 설정 오류를 표시합니다.
initializeKakaoMapProvider()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {/* ===== 페이지 주소와 화면을 연결하는 최상위 라우터 ===== */}
    <BrowserRouter>
      {/* ===== 강사님 f/login 구조를 적용한 전역 로그인 상태 ===== */}
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
