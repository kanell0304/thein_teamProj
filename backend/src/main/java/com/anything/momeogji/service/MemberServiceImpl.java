package com.anything.momeogji.service;

import com.anything.momeogji.dto.MemberSummaryResponse;
import com.anything.momeogji.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MemberSummaryResponse> getOtherMembers(Long currentMemberId) {
        return memberRepository.findAllByIdNotOrderByNicknameAsc(currentMemberId).stream()
                .map(member -> new MemberSummaryResponse(
                        member.getId(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                ))
                .toList();
    }
}
