package com.kiwobollae.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class KiwobollaeApplicationTimeProviderTest {

	@Test
	void auditingDateTimeProviderUsesSeoulTime() {
		LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		LocalDateTime auditedAt = LocalDateTime.from(
				new KiwobollaeApplication().seoulDateTimeProvider().getNow().orElseThrow()
		);
		LocalDateTime after = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

		assertThat(auditedAt).isBetween(before, after);
		assertThat(Duration.between(auditedAt, after)).isLessThan(Duration.ofSeconds(1));
	}
}
