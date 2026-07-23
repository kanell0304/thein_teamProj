import { useCallback, useEffect, useMemo, useState } from 'react'
import { postDevLogin, postKakaoLogin } from '../api/authApi'
import {
  AUTH_UNAUTHORIZED_EVENT,
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from '../api/axiosInstance'
import AuthContext from './authContext'

const AUTH_USER_STORAGE_KEY = 'momeokji.authUser'

// ===== 새로고침 후에도 로그인 사용자를 복구할 수 있도록 저장값을 안전하게 해석 =====
function readStoredUser() {
  if (!getAccessToken()) return null

  try {
    return JSON.parse(window.localStorage.getItem(AUTH_USER_STORAGE_KEY))
  } catch {
    window.localStorage.removeItem(AUTH_USER_STORAGE_KEY)
    return null
  }
}

// ===== 인증 응답을 화면에서 사용할 공통 사용자 형태로 변환 =====
function createAuthUser(response) {
  return {
    id: response.memberId,
    memberId: response.memberId,
    name: response.nickname,
    nickname: response.nickname,
    profileImageUrl: response.profileImageUrl ?? null,
  }
}

function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser)
  const [isAuthenticating, setIsAuthenticating] = useState(false)

  // ===== 로그인 응답의 토큰과 사용자 정보를 같은 시점에 저장 =====
  const completeLogin = useCallback((response) => {
    if (!response?.accessToken || response.memberId == null) {
      throw new Error('로그인 응답 정보가 올바르지 않습니다.')
    }

    const authenticatedUser = createAuthUser(response)
    setAccessToken(response.accessToken)
    window.localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(authenticatedUser))
    setUser(authenticatedUser)
    return authenticatedUser
  }, [])

  // ===== 저장된 인증 정보를 모두 삭제하고 공개 로그인 화면으로 전환 =====
  const logout = useCallback(() => {
    clearAccessToken()
    window.localStorage.removeItem(AUTH_USER_STORAGE_KEY)
    setUser(null)
  }, [])

  // ===== 개발용 로그인: 목업에서는 로컬 사용자, 실제 모드에서는 Spring JWT 사용 =====
  const loginForDevelopment = useCallback(async ({ kakaoId, nickname }) => {
    setIsAuthenticating(true)
    try {
      const response = String(import.meta.env.VITE_USE_MOCK ?? 'false').toLowerCase() === 'true'
        ? {
          accessToken: 'mock-access-token',
          memberId: 'member-me',
          nickname: nickname || '테스트유저',
        }
        : await postDevLogin({ kakaoId, nickname })
      return completeLogin(response)
    } catch (error) {
      throw new Error(error.userMessage || '개발용 로그인에 실패했습니다.', { cause: error })
    } finally {
      setIsAuthenticating(false)
    }
  }, [completeLogin])

  // ===== 카카오 인가 코드를 백엔드 JWT로 교환 =====
  const loginWithKakaoCode = useCallback(async (code) => {
    setIsAuthenticating(true)
    try {
      return completeLogin(await postKakaoLogin(code))
    } catch (error) {
      throw new Error(error.userMessage || '카카오 로그인에 실패했습니다.', { cause: error })
    } finally {
      setIsAuthenticating(false)
    }
  }, [completeLogin])

  // ===== 만료된 토큰으로 401 응답을 받으면 화면 로그인 상태도 함께 해제 =====
  useEffect(() => {
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, logout)
    return () => window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, logout)
  }, [logout])

  const contextValue = useMemo(() => ({
    user,
    isAuthenticated: Boolean(user && getAccessToken()),
    isAuthenticating,
    loginForDevelopment,
    loginWithKakaoCode,
    logout,
  }), [isAuthenticating, loginForDevelopment, loginWithKakaoCode, logout, user])

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  )
}

export default AuthProvider
