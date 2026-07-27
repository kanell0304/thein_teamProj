package com.anything.momeogji.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // nullable로 둔 이유: 이 필드가 추가되기 전부터 존재하던 채팅방이 이미 있어서, ddl-auto=update가
    // 기존 테이블에 NOT NULL 컬럼을 추가하는 걸 조용히 건너뛴다(로그만 남기고 실패). 대신 조회 시점에
    // 값이 없으면 발급해 채워 넣는 방식으로 처리한다 - assignJoinCode() 참고.
    /** 채팅방 중간 합류용 공유 코드. 이 필드 추가 이전에 만들어진 방은 null일 수 있다. */
    @Column(name = "join_code", unique = true, length = 8)
    private String joinCode;

    /** 코드가 없는 기존 채팅방에 조회 시점에 코드를 채워 넣는다. */
    public void assignJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }
}
