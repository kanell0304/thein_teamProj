import './NextProgressButton.css'

function NextProgressButton({
  currentStep = 1,
  totalSteps = 5,
  label = '다음',
  onClick,
  disabled = false,
}) {
  const safeTotal = Math.max(1, totalSteps)
  const safeCurrent = Math.min(Math.max(1, currentStep), safeTotal)

  return (
    <div className="next-progress-button">
      <div
        className="next-progress-button__bar"
        role="progressbar"
        aria-valuemin="1"
        aria-valuemax={safeTotal}
        aria-valuenow={safeCurrent}
      >
        {Array.from({ length: safeTotal }, (_, index) => (
          <span
            className={index < safeCurrent ? 'is-complete' : ''}
            key={index}
          />
        ))}
      </div>

      <button
        className="app-button app-button--primary app-button--large next-progress-button__action"
        type="button"
        onClick={onClick}
        disabled={disabled}
      >
        {label}
      </button>
    </div>
  )
}

export default NextProgressButton
