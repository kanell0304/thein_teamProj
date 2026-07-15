package com.anything.momeogji.controller.recommendation;

import com.anything.momeogji.dto.recommendation.FinalNoticeResponse;
import com.anything.momeogji.dto.recommendation.FinalizeRequest;
import com.anything.momeogji.dto.recommendation.VoteRequest;
import com.anything.momeogji.dto.recommendation.VoteTallyResult;
import com.anything.momeogji.service.recommendation.FinalNoticeService;
import com.anything.momeogji.service.recommendation.VotingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * DB 테이블이 아직 없으므로 투표 결과/추천 결과를 서버에 저장하지 않고 요청 본문으로 그대로 주고받는 프로토타입 엔드포인트.
 * 실제 서비스에서는 모임/추천 회차 ID로 조회하는 형태로 바뀔 예정.
 */
@RestController
@RequestMapping("/api/ai/recommendations/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VotingService votingService;
    private final FinalNoticeService finalNoticeService;

    /** 중복 투표를 포함한 투표 목록을 집계해 1등(공동 1등이면 랜덤 확정)을 반환한다. */
    @PostMapping("/tally")
    public VoteTallyResult tally(@RequestBody List<VoteRequest> votes) {
        return votingService.tally(votes);
    }

    /** 투표 결과 + 추천 결과 + 약속 시간을 받아 채팅방 공지에 표시할 최종 결과를 만든다. */
    @PostMapping("/finalize")
    public FinalNoticeResponse finalize(@Valid @RequestBody FinalizeRequest request) {
        return finalNoticeService.buildFinalNotice(
                request.recommendationResult(), request.tallyResult(), request.meetingTime());
    }
}
