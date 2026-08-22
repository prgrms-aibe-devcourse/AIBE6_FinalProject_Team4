package com.kiwobollae.api.payment.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class PaymentResponseJsonTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

	@Test
	void serializesAndRestoresLocalDateTimeAsIso8601String() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 7, 28, 16, 0);
		PaymentResponse response = new PaymentResponse(
				1L,
				2L,
				1_000L,
				1_000L,
				PaymentStatus.PENDING,
				PaymentProviderType.TOSS,
				"KWB-order",
				null,
				null,
				createdAt,
				"결제 요청이 생성되었습니다."
		);

		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ObjectMapper.class);
			ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

			String snapshot = objectMapper.writeValueAsString(response);
			PaymentResponse restored = objectMapper.readValue(snapshot, PaymentResponse.class);

			assertThat(snapshot)
					.contains("\"createdAt\":\"2026-07-28T16:00:00\"")
					.doesNotContain("\"createdAt\":[");
			assertThat(restored).isEqualTo(response);
		});
	}
}
