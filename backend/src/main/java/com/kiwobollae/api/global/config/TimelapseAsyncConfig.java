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

	// 대표이미지 S3 다운로드를 병렬로 돌리는 전용 풀. ForkJoinPool.commonPool()(기본값)을 그냥 쓰면
	// (1) 앱 전체가 공유하는 자원이라 여기서 스레드를 오래 붙잡으면 무관한 기능까지 느려지고,
	// (2) commonPool 기본 크기가 "CPU 코어 수 - 1"이라 코어가 적은 운영 인스턴스에서는 병렬도가
	// 거의 안 나온다(다운로드는 대기가 대부분인 I/O 작업이라 코어 수와 무관하게 늘려도 된다).
	// timelapseTaskExecutor(코어/맥스 1)는 절대 재사용하면 안 된다 — encodeAndUpload()를 실행 중인
	// 스레드가 바로 그 풀의 유일한 스레드라, 같은 풀에 하위 작업을 넣고 join()으로 기다리면
	// 그 하위 작업을 처리할 스레드가 자기 자신이라 영원히 대기(데드락)한다.
	@Bean(name = "timelapseDownloadExecutor")
	public Executor timelapseDownloadExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(8);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("timelapse-download-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
		return executor;
	}
}
