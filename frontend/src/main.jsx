import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.jsx'
import AuthProvider from './contexts/AuthProvider.jsx'
import { initializeKakaoMapProvider } from './services/kakaoMapApi'

// ===== 카카오맵 공급자 등록 =====
// 키가 없으면 기존 데모 장소 검색이 자동으로 유지됩니다.
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
