package com.kiwobollae.api.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TossPaymentCircuitBreakerTest {

	@Test
	void opensAfterConsecutiveProviderFailuresAndClosesAfterSuccessfulProbe() {
		AtomicLong now = new AtomicLong(1L);
		TossPaymentCircuitBreaker circuitBreaker = new TossPaymentCircuitBreaker(
				2,
				Duration.ofSeconds(30),
				now::get
		);
		AtomicInteger calls = new AtomicInteger();

		for (int attempt = 0; attempt < 2; attempt++) {
			assertThatThrownBy(() -> circuitBreaker.execute(() -> {
				calls.incrementAndGet();
				throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
			})).isInstanceOf(BusinessException.class);
		}

		assertThatThrownBy(() -> circuitBreaker.execute(() -> {
			calls.incrementAndGet();
			return "blocked";
		})).isInstanceOf(PaymentProviderBusyException.class);
		assertThat(calls).hasValue(2);

		now.addAndGet(Duration.ofSeconds(31).toNanos());
		assertThat(circuitBreaker.execute(() -> {
			calls.incrementAndGet();
			return "recovered";
		})).isEqualTo("recovered");
		assertThat(circuitBreaker.execute(() -> "closed")).isEqualTo("closed");
		assertThat(calls).hasValue(3);
	}

	@Test
	void bulkheadRejectionDoesNotOpenCircuit() {
		TossPaymentCircuitBreaker circuitBreaker = new TossPaymentCircuitBreaker(
				1,
				Duration.ofSeconds(30),
				() -> 1L
		);

		assertThatThrownBy(() -> circuitBreaker.execute(() -> {
			throw new PaymentProviderBusyException();
		})).isInstanceOf(PaymentProviderBusyException.class);
		assertThat(circuitBreaker.execute(() -> "still-closed")).isEqualTo("still-closed");
	}

	@Test
	void rejectsInvalidConfiguration() {
		assertThatThrownBy(() ->
				new TossPaymentCircuitBreaker(0, Duration.ofSeconds(30), System::nanoTime))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
				new TossPaymentCircuitBreaker(5, Duration.ZERO, System::nanoTime))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
