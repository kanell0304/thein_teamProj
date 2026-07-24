package com.anything.momeogji.controller.chat;

import com.anything.momeogji.dto.common.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ChatExceptionHandlerTest {

    private final ChatExceptionHandler handler = new ChatExceptionHandler();

    @Test
    void mapsSemanticRequestValidationFailureToBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/chatrooms/7/menu-keywords"
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidInput(
                new IllegalArgumentException("요청자는 모먹지 참가자에 포함되어야 합니다."),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_CHAT_REQUEST");
        assertThat(response.getBody().message())
                .isEqualTo("요청자는 모먹지 참가자에 포함되어야 합니다.");
        assertThat(response.getBody().path()).isEqualTo("/api/chatrooms/7/menu-keywords");
    }
}
