import MainTabBar from '../navigation/MainTabBar'
import MobileStatusBar from './MobileStatusBar'
import './MainScreen.css'

function MainScreen({ title, headerActions, children }) {
  return (
    <main className="main-screen-page">
      <section className="main-screen-shell" aria-labelledby="main-screen-title">
        <MobileStatusBar />
        <header className="main-screen-header">
          <h1 id="main-screen-title">{title}</h1>
          {headerActions && <div className="main-screen-actions">{headerActions}</div>}
        </header>
        <div className="main-screen-content">{children}</div>
        <MainTabBar />
      </section>
    </main>
  )
}

export default MainScreen
