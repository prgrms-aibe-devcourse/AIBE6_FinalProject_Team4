package com.kiwobollae.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class KiwobollaeApplicationTimeProviderTest {

	@Test
	void auditingDateTimeProviderUsesSeoulTime() {
		ZoneId seoul = ZoneId.of("Asia/Seoul");
		Clock fixedSeoulClock = Clock.fixed(Instant.parse("2026-07-31T01:00:00Z"), seoul);
		LocalDateTime auditedAt = LocalDateTime.from(
				new KiwobollaeApplication()
						.seoulDateTimeProvider(fixedSeoulClock)
						.getNow()
						.orElseThrow()
		);

		assertThat(auditedAt).isEqualTo(LocalDateTime.of(2026, 7, 31, 10, 0));
		assertThat(new KiwobollaeApplication().seoulClock().getZone()).isEqualTo(seoul);
	}
}
