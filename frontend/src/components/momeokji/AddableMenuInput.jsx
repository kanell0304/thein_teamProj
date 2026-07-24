import { useId } from 'react'
import './AddableMenuInput.css'

/** 메뉴·기피 음식처럼 직접 입력한 값을 선택 칩 목록에 추가하는 공용 컴포넌트. */
function AddableMenuInput({
  value,
  onChange,
  onAdd,
  placeholder = '메뉴 직접 입력',
  ariaLabel = '메뉴 직접 입력',
  maxLength,
  errorMessage = '',
  disabled = false,
}) {
  const errorId = useId()
  const canAdd = Boolean(value.trim()) && !errorMessage && !disabled

  const handleKeyDown = (event) => {
    if (event.key !== 'Enter') return
    event.preventDefault()
    if (canAdd) onAdd()
  }

  return (
    <div className="addable-menu-field">
      <div className={`addable-menu-input${errorMessage ? ' is-invalid' : ''}`}>
        <input
          value={value}
          placeholder={placeholder}
          aria-label={ariaLabel}
          aria-invalid={Boolean(errorMessage)}
          aria-describedby={errorMessage ? errorId : undefined}
          maxLength={maxLength}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={handleKeyDown}
        />
        <button
          className="app-button app-button--primary app-button--small"
          type="button"
          disabled={!canAdd}
          onClick={onAdd}
        >
          추가
        </button>
      </div>
      {errorMessage && (
        <p className="addable-menu-input__error" id={errorId} role="alert">
          {errorMessage}
        </p>
      )}
    </div>
  )
}

export default AddableMenuInput
