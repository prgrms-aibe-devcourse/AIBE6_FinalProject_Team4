package com.kiwobollae.api.ai.client;

import java.util.Map;

public record AiJsonSchema(String name, String description, Map<String, Object> schema) {
  public AiJsonSchema {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("AI 응답 스키마 이름은 비어 있을 수 없습니다.");
    }
    if (schema == null || schema.isEmpty()) {
      throw new IllegalArgumentException("AI 응답 JSON Schema가 필요합니다.");
    }
    schema = Map.copyOf(schema);
  }
}
