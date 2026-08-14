package com.kiwobollae.api.point.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 포인트 원장 발생 시각을 제공한다. 일반 요청은 KST 현재 시각, 데이터 이관·시드는 지정 시각을 사용한다.
 *
 * <p>이 컴포넌트는 현재 스레드의 {@link ThreadLocal} 값만 바꾸며 스레드 전환이나 트랜잭션 시작·전파를 하지 않는다.
 * 따라서 {@link #runAt(LocalDateTime, Runnable)} 내부 작업은 호출자가 연 트랜잭션에 그대로 참여한다.
 */
@Component
@RequiredArgsConstructor
public class PointTransactionTimeProvider {

	private final Clock seoulClock;
	private final ThreadLocal<LocalDateTime> override = new ThreadLocal<>();

	public LocalDateTime now() {
		LocalDateTime overridden = override.get();
		return overridden == null ? LocalDateTime.now(seoulClock) : overridden;
	}

	/** 현재 스레드에서 동기 실행되는 하나의 작업에만 지정 시각을 적용하고 반드시 이전 상태로 복원한다. */
	public <T> T callAt(LocalDateTime occurredAt, Supplier<T> action) {
		LocalDateTime previous = override.get();
		override.set(occurredAt);
		try {
			return action.get();
		} finally {
			restore(previous);
		}
	}

	public void runAt(LocalDateTime occurredAt, Runnable action) {
		callAt(occurredAt, () -> {
			action.run();
			return null;
		});
	}

	private void restore(LocalDateTime previous) {
		if (previous == null) {
			override.remove();
		} else {
			override.set(previous);
		}
	}
}
