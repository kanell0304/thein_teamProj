// ===== 아이콘 글꼴 로딩 없이 일정하게 보이는 헤더 추가 SVG 아이콘 =====
export function FriendAddIcon() {
  return (
    <svg className="main-screen-action-icon" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="9" cy="7" r="3.25" />
      <path d="M3.5 18.5c.4-3.1 2.25-5 5.5-5s5.1 1.9 5.5 5" />
      <path d="M18 8.5v6M15 11.5h6" />
    </svg>
  )
}

export function ChatAddIcon() {
  return (
    <svg className="main-screen-action-icon" viewBox="0 0 24 24" aria-hidden="true">
      {/* ===== 말풍선과 플러스가 겹치지 않도록 좌·우 영역을 분리 ===== */}
      <path d="M4 6h7.5A2.5 2.5 0 0 1 14 8.5v4a2.5 2.5 0 0 1-2.5 2.5H8l-4 3v-9.5A2.5 2.5 0 0 1 6.5 6Z" />
      <path d="M18.5 3.5v6M15.5 6.5h6" />
    </svg>
  )
}
