package com.anything.momeogji.service.recommendation;

/** OpenAI Chat Completions 호출을 추상화한 인터페이스. 테스트에서는 이 인터페이스를 모킹해 실제 네트워크 호출 없이 검증한다. */
public interface OpenAiChatClient {

    /**
     * @return 모델이 JSON 스키마에 맞춰 생성한 content 문자열(파싱 전 원본 JSON)
     */
    String requestStructuredJson(String systemPrompt, String userPrompt);
}
