package com.anything.momeogji.service;

import com.anything.momeogji.dto.MemberSummaryResponse;

import java.util.List;

public interface MemberService {

    List<MemberSummaryResponse> getOtherMembers(Long currentMemberId);
}
