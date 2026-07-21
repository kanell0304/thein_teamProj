import { createContext } from 'react'

// ===== 인증 Provider와 소비 훅이 공유하는 Context 객체 =====
const AuthContext = createContext(null)

export default AuthContext
