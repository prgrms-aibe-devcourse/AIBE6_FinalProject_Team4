package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

class GachaRewardEventListenerTest {

  @Test
  void usesDedicatedAsyncExecutor() throws NoSuchMethodException {
    Method listenerMethod =
        GachaRewardEventListener.class.getDeclaredMethod(
            "onRewardCreated", GachaRewardCreatedEvent.class);

    Async async = listenerMethod.getAnnotation(Async.class);

    assertThat(async).isNotNull();
    assertThat(async.value()).isEqualTo("gachaTaskExecutor");
  }
}
