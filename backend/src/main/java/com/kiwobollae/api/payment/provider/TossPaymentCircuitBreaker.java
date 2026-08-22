package com.kiwobollae.api.payment.provider;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TossPaymentCircuitBreaker {

	private final int failureThreshold;
	private final long openDurationNanos;
	private final LongSupplier nanoTime;
	private final AtomicInteger consecutiveFailures = new AtomicInteger();
	private final AtomicLong openUntilNanos = new AtomicLong();

	@Autowired
	public TossPaymentCircuitBreaker(
			@Value("${payment.toss.circuit-breaker.failure-threshold:5}") int failureThreshold,
			@Value("${payment.toss.circuit-breaker.open-duration:30s}") Duration openDuration
	) {
		this(failureThreshold, openDuration, System::nanoTime);
	}

	TossPaymentCircuitBreaker(
			int failureThreshold,
			Duration openDuration,
			LongSupplier nanoTime
	) {
		if (failureThreshold < 1) {
			throw new IllegalArgumentException("Toss Payments 회로 차단 실패 기준은 1 이상이어야 합니다.");
		}
		if (openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
			throw new IllegalArgumentException("Toss Payments 회로 차단 시간은 0보다 커야 합니다.");
		}
		this.failureThreshold = failureThreshold;
		this.openDurationNanos = openDuration.toNanos();
		this.nanoTime = nanoTime;
	}

	<T> T execute(Supplier<T> operation) {
		if (nanoTime.getAsLong() < openUntilNanos.get()) {
			throw new PaymentProviderBusyException();
		}
		try {
			T result = operation.get();
			recordSuccess();
			return result;
		} catch (PaymentProviderBusyException exception) {
			throw exception;
		} catch (BusinessException exception) {
			if (isProviderFailure(exception)) {
				recordFailure();
			}
			throw exception;
		}
	}

	private boolean isProviderFailure(BusinessException exception) {
		return exception.getErrorCode() == ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE
				|| exception.getErrorCode() == ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE;
	}

	private void recordSuccess() {
		consecutiveFailures.set(0);
		openUntilNanos.set(0L);
	}

	private void recordFailure() {
		if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
			openUntilNanos.set(nanoTime.getAsLong() + openDurationNanos);
		}
	}
}
