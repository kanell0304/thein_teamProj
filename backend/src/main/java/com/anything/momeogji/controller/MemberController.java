package com.anything.momeogji.controller;

import com.anything.momeogji.dto.MemberSummaryResponse;
import com.anything.momeogji.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@Tag(name = "회원", description = "친구와 모임 참가자 선택에 사용하는 회원 API")
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final MemberService memberService;

    // ===== 로그인한 본인을 제외해 친구·참가자 후보 목록 조회 =====
    @Operation(summary = "사용자 목록", description = "현재 로그인한 회원을 제외한 사용자 목록을 조회합니다.")
    @GetMapping
    public List<MemberSummaryResponse> getMembers(Authentication authentication) {
        return memberService.getOtherMembers((Long) authentication.getPrincipal());
    }
}
