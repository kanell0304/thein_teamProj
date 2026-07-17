import { useState } from 'react'
import './ParticipantPicker.css'

function ParticipantPicker({ people, selectedIds, requiredIds = [], onChange }) {
  const [isOpen, setIsOpen] = useState(false)
  const names = people.filter((person) => selectedIds.includes(person.id)).map((person) => person.name)

  const togglePerson = (id) => {
    // 주최자처럼 필수 참가자로 지정된 사용자는 목록에서 제외할 수 없습니다.
    if (requiredIds.includes(id)) return
    onChange(selectedIds.includes(id)
      ? selectedIds.filter((selectedId) => selectedId !== id)
      : [...selectedIds, id])
  }

  return (
    <div className="participant-picker">
      <button className="participant-picker__trigger" type="button" aria-expanded={isOpen} onClick={() => setIsOpen((previous) => !previous)}>
        <span>{names.length ? `${names.join(', ')} (${names.length}명)` : '참가자를 선택해주세요'}</span>
        <span className="participant-picker__chevron" aria-hidden="true" />
      </button>
      {isOpen && (
        <div className="participant-picker__menu">
          {people.map((person) => {
            const isRequired = requiredIds.includes(person.id)

            return (
              <label className={isRequired ? 'is-required' : ''} key={person.id}>
                <input
                  type="checkbox"
                  name="participantIds"
                  value={person.id}
                  checked={selectedIds.includes(person.id)}
                  disabled={isRequired}
                  onChange={() => togglePerson(person.id)}
                />
                <span className="participant-picker__avatar" aria-hidden="true">{person.name.slice(0, 1)}</span>
                <span>{person.name}</span>
                {isRequired && <em>주최자 · 필수</em>}
              </label>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default ParticipantPicker
