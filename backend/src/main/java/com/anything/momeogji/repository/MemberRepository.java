package com.anything.momeogji.repository;

import com.anything.momeogji.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoId(String kakaoId);
}
