package com.kiwobollae.api.notification.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
public class JournalReminderAsyncConfig {

	private static final int QUEUE_CAPACITY = 200;

	// 로그인 응답을 막지 않기 위해 리마인더 기록을 별도 스레드에서 처리한다. 실패해도
	// 다음 로그인 때 다시 시도되는 best-effort 로직이라 큐가 꽉 차 요청이 버려져도
	// 로그로만 남기고 재시도는 하지 않는다.
	@Bean(name = "journalReminderTaskExecutor")
	public Executor journalReminderTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(QUEUE_CAPACITY);
		executor.setThreadNamePrefix("journal-reminder-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.setRejectedExecutionHandler((runnable, executorService) ->
				log.warn("Journal reminder task queue is full (capacity={}); a reminder was dropped for this login.",
						QUEUE_CAPACITY));
		executor.initialize();
		return executor;
	}
}
