package com.kiwobollae.api.commerce.gacha.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class GachaAsyncConfigTest {

  private final GachaAsyncConfig config = new GachaAsyncConfig();

  @Test
  void createsSingleThreadedDedicatedExecutor() {
    Executor configuredExecutor = config.gachaTaskExecutor();

    assertThat(configuredExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configuredExecutor;
    try {
      assertThat(executor.getCorePoolSize()).isEqualTo(1);
      assertThat(executor.getMaxPoolSize()).isEqualTo(1);
      assertThat(executor.getThreadNamePrefix()).isEqualTo("gacha-draw-");
    } finally {
      executor.shutdown();
    }
  }
}
