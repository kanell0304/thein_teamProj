import { useState } from 'react'
import './ParticipantPicker.css'

function ParticipantPicker({ people, selectedIds, onChange }) {
  const [isOpen, setIsOpen] = useState(false)
  const names = people.filter((person) => selectedIds.includes(person.id)).map((person) => person.name)

  const togglePerson = (id) => {
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
          {people.map((person) => (
            <label key={person.id}>
              <input
                type="checkbox"
                name="participantIds"
                value={person.id}
                checked={selectedIds.includes(person.id)}
                onChange={() => togglePerson(person.id)}
              />
              <span className="participant-picker__avatar" aria-hidden="true">{person.name.slice(0, 1)}</span>
              <span>{person.name}</span>
            </label>
          ))}
        </div>
      )}
    </div>
  )
}

export default ParticipantPicker
