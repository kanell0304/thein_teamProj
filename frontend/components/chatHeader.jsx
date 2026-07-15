import React from "react";
import {
  ChevronLeft,
  Search,
  Menu,
  BatteryFull,
} from "lucide-react";

function ChatHeader(props) {
  return (
    <header className="chat-header">
      <div className="status-bar">
        <span>5G</span>
        <BatteryFull size={20} aria-label="배터리" />
      </div>

      <div className="top-bar">
        <button
          className="back-btn"
          type="button"
          aria-label="뒤로 가기"
        >
          <ChevronLeft size={28} />
        </button>

        <h1>{props.title}</h1>

        <div className="header-icons">
          <button type="button" aria-label="검색">
            <Search size={22} />
          </button>

          <button type="button" aria-label="메뉴">
            <Menu size={23} />
          </button>
        </div>
      </div>
    </header>
  );
}

export default ChatHeader;

