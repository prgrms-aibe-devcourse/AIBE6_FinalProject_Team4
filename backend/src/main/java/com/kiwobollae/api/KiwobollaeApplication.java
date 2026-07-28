package com.kiwobollae.api;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing(dateTimeProviderRef = "seoulDateTimeProvider")
@SpringBootApplication
public class KiwobollaeApplication {

	private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

	public static void main(String[] args) {
		SpringApplication.run(KiwobollaeApplication.class, args);
	}

	/** DB 저장 기준을 Asia/Seoul로 통일하는 JPA 감사 시각 공급자. */
	@Bean
	DateTimeProvider seoulDateTimeProvider() {
		return () -> Optional.of(LocalDateTime.now(SEOUL_ZONE_ID));
	}
}
