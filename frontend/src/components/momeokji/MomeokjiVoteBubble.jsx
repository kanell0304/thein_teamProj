import momeokjiIcon from '../../assets/icons/momeokji-icon.png'
import './MomeokjiVoteBubble.css'

// ===== 채팅 버블에 표시할 투표 단계별 문구 =====
const BUBBLE_COPY = {
  CREATED: {
    description: '메뉴 투표가 열렸습니다.',
    buttonLabel: '투표하러 가기',
  },
  IN_PROGRESS: {
    description: '메뉴 투표가 진행 중입니다.',
    buttonLabel: '투표하러 가기',
  },
  CLOSED: {
    description: '투표 결과를 확인해 주세요.',
    buttonLabel: '결과 확인하기',
  },
  EXPIRED: {
    description: '투표 시간이 만료되었습니다.',
    buttonLabel: '만료 결과 확인하기',
  },
}

/** 설정 완료 시 채팅창에 추가되는 카카오톡형 모먹지 투표 연결 버블. */
function MomeokjiVoteBubble({ status = 'CREATED', onOpen }) {
  const copy = BUBBLE_COPY[status] ?? BUBBLE_COPY.CREATED

  return (
    <article className={`momeokji-vote-bubble momeokji-vote-bubble--${status.toLowerCase()}`}>
      <div className="momeokji-vote-bubble__brand">
        <span className="momeokji-vote-bubble__icon" aria-hidden="true">
          <img src={momeokjiIcon} alt="" />
        </span>
        <strong>오늘 <em>모 먹지?</em></strong>
      </div>
      <p>{copy.description}</p>
      <button type="button" disabled={!onOpen} onClick={onOpen}>{copy.buttonLabel}</button>
    </article>
  )
}

export default MomeokjiVoteBubble
