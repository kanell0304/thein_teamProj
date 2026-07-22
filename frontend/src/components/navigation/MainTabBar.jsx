import { NavLink } from 'react-router-dom'
import './MainTabBar.css'

const MAIN_TABS = [
  { to: '/friends', label: '친구', icon: 'person' },
  { to: '/chats', label: '채팅', icon: 'chat_bubble' },
  { to: '/settings', label: '설정', icon: 'settings' },
]

function MainTabBar() {
  return (
    <nav className="main-tab-bar" aria-label="주요 화면">
      {MAIN_TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          className={({ isActive }) => `main-tab${isActive ? ' is-active' : ''}`}
        >
          <span className="material-symbols-outlined" aria-hidden="true">{tab.icon}</span>
          <span>{tab.label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

export default MainTabBar
