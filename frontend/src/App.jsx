import { Navigate, Route, Routes } from 'react-router-dom'
import PrivateRoute from './components/auth/PrivateRoute'
import useAuth from './hooks/useAuth'
import ChatRoomPage from './pages/ChatRoomPage'
import ChatListPage from './pages/ChatListPage'
import FriendListPage from './pages/FriendListPage'
import KakaoCallbackPage from './pages/KakaoCallbackPage'
import LoginPage from './pages/LoginPage'
import NewChatRoomPage from './pages/NewChatRoomPage'
import SettingsPage from './pages/SettingsPage'

// ===== 인증 사용자를 현재 채팅방 참가자 형태로 전달 =====
function AuthenticatedChatRoom() {
  const { user } = useAuth()
  return <ChatRoomPage currentUser={user} />
}

function App() {
  return (
    <Routes>
      {/* ===== 로그인과 OAuth 콜백은 인증 없이 접근 가능 ===== */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/oauth/kakao/callback" element={<KakaoCallbackPage />} />

      {/* ===== 강사님 PrivateRoute 방식으로 채팅 화면 보호 ===== */}
      <Route element={<PrivateRoute />}>
        <Route path="/" element={<Navigate to="/chats" replace />} />
        <Route path="/friends" element={<FriendListPage />} />
        <Route path="/chats" element={<ChatListPage />} />
        <Route path="/chats/new" element={<NewChatRoomPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/chat/:roomId" element={<AuthenticatedChatRoom />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
