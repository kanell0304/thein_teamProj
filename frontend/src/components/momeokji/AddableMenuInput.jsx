import './AddableMenuInput.css'

/** 메뉴·기피 음식처럼 직접 입력한 값을 선택 칩 목록에 추가하는 공용 컴포넌트. */
function AddableMenuInput({
  value,
  onChange,
  onAdd,
  placeholder = '메뉴 직접 입력',
  ariaLabel = '메뉴 직접 입력',
}) {
  const handleKeyDown = (event) => {
    if (event.key !== 'Enter') return
    event.preventDefault()
    onAdd()
  }

  return (
    <div className="addable-menu-input">
      <input
        value={value}
        placeholder={placeholder}
        aria-label={ariaLabel}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={handleKeyDown}
      />
      <button
        className="app-button app-button--primary app-button--small"
        type="button"
        disabled={!value.trim()}
        onClick={onAdd}
      >
        추가
      </button>
    </div>
  )
}

export default AddableMenuInput
