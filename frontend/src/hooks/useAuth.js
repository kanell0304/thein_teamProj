import { useContext } from 'react'
import AuthContext from '../contexts/authContext'

// ===== 인증 Context를 화면과 보호 라우트에서 안전하게 사용 =====
export default function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth는 AuthProvider 내부에서 사용해야 합니다.')
  return context
}
