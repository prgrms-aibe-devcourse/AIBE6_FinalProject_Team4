package com.kiwobollae.api.payment.provider;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TossPaymentBulkhead {

	private final Semaphore permits;
	private final Duration acquireTimeout;

	@Autowired
	public TossPaymentBulkhead(
			@Value("${payment.toss.bulkhead.max-concurrent-calls:10}") int maxConcurrentCalls,
			@Value("${payment.toss.bulkhead.acquire-timeout:100ms}") Duration acquireTimeout
	) {
		if (maxConcurrentCalls < 1) {
			throw new IllegalArgumentException("Toss Payments 최대 동시 호출 수는 1 이상이어야 합니다.");
		}
		if (acquireTimeout == null || acquireTimeout.isNegative()) {
			throw new IllegalArgumentException("Toss Payments 호출 대기 시간은 0 이상이어야 합니다.");
		}
		this.permits = new Semaphore(maxConcurrentCalls, true);
		this.acquireTimeout = acquireTimeout;
	}

	<T> T execute(Supplier<T> operation) {
		boolean acquired = false;
		try {
			acquired = permits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!acquired) {
				throw new PaymentProviderBusyException();
			}
			return operation.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new PaymentProviderBusyException();
		} finally {
			if (acquired) {
				permits.release();
			}
		}
	}
}
