package com.anything.momeogji.config.websocket;

import com.anything.momeogji.config.AppCorsProperties;
import com.anything.momeogji.config.auth.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 * 클라이언트 → 서버: /app/** (예: /app/chatrooms/1/messages)
 * 서버 → 클라이언트 브로드캐스트: /topic/** (예: /topic/chatrooms/1)
 * 지금 규모(서버 1대)에서는 Spring 내장 심플 브로커로 충분해서 별도 메시지 브로커(RabbitMQ 등)는 쓰지 않는다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final AppCorsProperties corsProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // app.cors.allowed-origins로 지정한 프론트 origin만 핸드셰이크를 허용한다(application.properties 참고).
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
