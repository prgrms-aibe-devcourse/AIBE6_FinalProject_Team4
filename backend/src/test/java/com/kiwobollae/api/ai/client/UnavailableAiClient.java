package com.kiwobollae.api.ai.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * test 프로파일에서 {@link AiClient} 자리를 채우는 대체 빈.
 *
 * <p>{@link OpenAiClient}는 {@code @Profile("!test")}로 테스트에서 제외된다 — 테스트가 실수로 실제 OpenAI를 호출하는 일이 절대
 * 없게 하려는 의도다. 그런데 {@code AiClient}를 주입받는 빈이 생기면 그 제외 때문에 앱 컨텍스트 자체가 뜨지 못하고, {@code @SpringBootTest}
 * 전체가 무너진다.
 *
 * <p>그래서 빈은 존재하게 두되 호출하면 예외를 던진다. 컨텍스트 로딩과 "실제 호출 금지"를 동시에 지킨다.
 *
 * <p>AI를 실제로 거치는 테스트는 이 빈에 기대지 말고 {@code AiClient}를 직접 mock으로 대체해야 한다. 이 예외가 보인다면 그 대체를 빠뜨린 것이다.
 */
@Component
@Profile("test")
public class UnavailableAiClient implements AiClient {

  @Override
  public AiResponse generate(AiRequest request) {
    throw new UnsupportedOperationException(
        "test 프로파일에서는 실제 AI를 호출할 수 없습니다. 이 테스트에서 AiClient를 mock으로 대체하세요.");
  }
}
