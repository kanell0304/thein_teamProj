package com.anything.momeogji.repository;

import com.anything.momeogji.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoId(String kakaoId);

    List<Member> findAllByIdNotOrderByNicknameAsc(Long memberId);
}
