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

  /**
   * OpenAI Responses API의 {@code input_image.detail}에 그대로 전송되는 값.
   *
   * <p>허용 값은 {@code auto} / {@code low} / {@code high} 세 개뿐이며, 그 외 값을 보내면 요청이 400으로 거부된다. 그래서 와이어
   * 값을 enum 이름에서 파생시키지 않고 상수마다 명시한다 — 새 상수를 추가하면 컴파일러가 와이어 값을 요구하므로, API가 모르는 값이 실수로 나가지 않는다.
   */
  public enum Detail {
    AUTO("auto"),
    LOW("low"),
    HIGH("high");

    private final String wireValue;

    Detail(String wireValue) {
      this.wireValue = wireValue;
    }

    public String wireValue() {
      return wireValue;
    }
  }
}
