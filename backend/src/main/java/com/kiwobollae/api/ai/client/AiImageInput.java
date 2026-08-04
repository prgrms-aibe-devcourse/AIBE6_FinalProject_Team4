package com.kiwobollae.api.ai.client;

public record AiImageInput(String imageUrl, Detail detail) {

  public AiImageInput {
    if (imageUrl == null || imageUrl.isBlank()) {
      throw new IllegalArgumentException("AI 이미지 URL은 비어 있을 수 없습니다.");
    }
    if (detail == null) {
      detail = Detail.AUTO;
    }
  }

  public enum Detail {
    AUTO,
    LOW,
    HIGH,
    ORIGINAL
  }
}
