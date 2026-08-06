package com.kiwobollae.api.ai.client;

import com.kiwobollae.api.ai.config.AiImageProperties;
import com.kiwobollae.api.ai.config.OpenAiProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * 스모크 테스트가 실제 OpenAI를 호출하는 클라이언트를 만들 때 쓰는 팩토리.
 *
 * <p>{@link OpenAiClient}의 생성자와 RestClient 팩토리는 package-private이다. 다른 패키지의 스모크 테스트에서 쓰려면 가시성을 넓혀야
 * 하는데, 운영 코드의 캡슐화를 테스트 편의로 깨는 대신 같은 패키지에 테스트 전용 진입점을 둔다.
 *
 * <p>이미지 공개 주소는 빈 값이다 — 텍스트 기능만 대상이라 이미지 URL을 해석할 일이 없다.
 */
public final class RealOpenAiClients {

  private RealOpenAiClients() {}

  public static OpenAiClient create(OpenAiProperties properties) {
    return new OpenAiClient(
        OpenAiClient.createRestClientBuilder(properties),
        new ObjectMapper(),
        properties,
        new AiImageUrlResolver(new AiImageProperties("")));
  }
}
