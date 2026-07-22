import { Navigate, Outlet, useLocation } from 'react-router-dom'
import useAuth from '../../hooks/useAuth'

// ===== 인증되지 않은 사용자를 로그인 화면으로 보내고 기존 목적지를 보존 =====
function PrivateRoute() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  return isAuthenticated
    ? <Outlet />
    : <Navigate to="/login" replace state={{ from: location }} />
}

export default PrivateRoute
