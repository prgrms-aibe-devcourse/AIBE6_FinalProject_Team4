package com.kiwobollae.api.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TossPaymentBulkheadTest {

	@Test
	void rejectsAdditionalCallQuicklyWhenAllPermitsAreOccupied() throws Exception {
		TossPaymentBulkhead bulkhead = new TossPaymentBulkhead(1, Duration.ZERO);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<String> first = executor.submit(() -> bulkhead.execute(() -> {
				entered.countDown();
				try {
					if (!release.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Bulkhead test release timed out");
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
				return "first";
			}));
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

			assertThatThrownBy(() -> bulkhead.execute(() -> "second"))
					.isInstanceOfSatisfying(BusinessException.class, exception ->
							assertThat(exception.getErrorCode())
									.isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE));

			release.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
			assertThat(bulkhead.execute(() -> "after-release")).isEqualTo("after-release");
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void rejectsInvalidConfiguration() {
		assertThatThrownBy(() -> new TossPaymentBulkhead(0, Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TossPaymentBulkhead(1, Duration.ofMillis(-1)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
