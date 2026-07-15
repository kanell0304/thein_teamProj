package com.anything.momeogji.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

// TODO: Member 담당자가 실제 필드/매핑으로 채워야 하는 임시 스텁. 로컬 부팅을 막지 않도록 id만 추가함.
@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
