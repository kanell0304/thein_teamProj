import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { initializeKakaoMapProvider } from './services/kakaoMapApi'

// ===== 카카오맵 공급자 등록 =====
// 키가 없으면 기존 데모 장소 검색이 자동으로 유지됩니다.
initializeKakaoMapProvider()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />

  </StrictMode>,
)
