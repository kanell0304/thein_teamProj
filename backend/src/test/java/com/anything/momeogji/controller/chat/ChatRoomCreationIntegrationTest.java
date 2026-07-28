package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.chat.ChatRoomCreateRequest;
import com.anything.momeogji.entity.Member;
import com.anything.momeogji.repository.MemberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatRoomCreationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 새_채팅방은_요청한_회원만_중복없이_포함하고_메시지는_비어있다() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Member host = memberRepository.saveAndFlush(member("host-" + suffix, "호스트"));
        Member participant = memberRepository.saveAndFlush(member("participant-" + suffix, "참가자"));
        memberRepository.saveAndFlush(member("outsider-" + suffix, "비참가자"));
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(host.getId(), null, List.of());

        ChatRoomCreateRequest request = new ChatRoomCreateRequest(
                "통합 검증 방",
                List.of(participant.getId(), participant.getId(), host.getId()));
        MvcResult createResult = mockMvc.perform(post("/api/chatrooms")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createdRoom = objectMapper.readTree(createResult.getResponse().getContentAsByteArray());
        long chatRoomId = createdRoom.get("id").asLong();

        mockMvc.perform(get("/api/chatrooms/{chatRoomId}/members", chatRoomId)
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        host.getId().intValue(), participant.getId().intValue())));

        mockMvc.perform(get("/api/chatrooms/{chatRoomId}/messages", chatRoomId)
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Member member(String kakaoId, String nickname) {
        return Member.builder()
                .kakaoId(kakaoId)
                .nickname(nickname)
                .build();
    }
}
