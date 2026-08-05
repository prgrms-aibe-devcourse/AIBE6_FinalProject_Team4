package com.kiwobollae.api.global.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
public class TimelapseAsyncConfig {

	private static final int QUEUE_CAPACITY = 100;

	@Bean(name = "timelapseTaskExecutor")
	public Executor timelapseTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(QUEUE_CAPACITY);
		executor.setThreadNamePrefix("timelapse-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		// 큐(100)까지 꽉 차면 기본 정책(AbortPolicy)이 RejectedExecutionException을 던지는데,
		// @Async 제출 시점(트랜잭션 커밋 콜백)에서 이걸 받아줄 코드가 없어 조용히 요청이 사라진다.
		// 자동 재시도/복구는 범위 밖(functional-spec §8)이라, 여기서는 최소한 로그로 남겨
		// 운영에서 "요청이 씹혔다"는 걸 알 수 있게 한다.
		executor.setRejectedExecutionHandler((runnable, executorService) ->
				log.error("Timelapse task queue is full (capacity={}); a timelapse request was dropped and its row will stay PENDING.",
						QUEUE_CAPACITY));
		executor.initialize();
		return executor;
	}
}
