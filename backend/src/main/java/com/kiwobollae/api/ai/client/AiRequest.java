package com.kiwobollae.api.ai.client;

import java.util.List;

public record AiRequest(
    AiModelRole modelRole,
    String systemPrompt,
    String userPrompt,
    List<AiImageInput> images,
    AiJsonSchema responseSchema) {
  public AiRequest {
    if (modelRole == null) {
      throw new IllegalArgumentException("AI 모델 역할이 필요합니다.");
    }
    if (systemPrompt == null || systemPrompt.isBlank()) {
      throw new IllegalArgumentException("AI 시스템 프롬프트는 비어 있을 수 없습니다.");
    }
    if (userPrompt == null || userPrompt.isBlank()) {
      throw new IllegalArgumentException("AI 사용자 프롬프트는 비어 있을 수 없습니다.");
    }
    images = images == null ? List.of() : List.copyOf(images);
    if (modelRole == AiModelRole.TEXT && !images.isEmpty()) {
      throw new IllegalArgumentException("텍스트 모델 요청에는 이미지 입력을 사용할 수 없습니다.");
    }
    if (responseSchema == null) {
      throw new IllegalArgumentException("AI 구조화 응답 스키마가 필요합니다.");
    }
  }
}
