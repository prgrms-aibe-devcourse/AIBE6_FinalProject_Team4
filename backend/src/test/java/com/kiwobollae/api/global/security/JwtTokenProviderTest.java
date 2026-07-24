package com.kiwobollae.api.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private final JwtTokenProvider provider = new JwtTokenProvider(
			"test_secret_key_needs_to_be_at_least_32_bytes_long",
			3600000L,
			1209600000L
	);

	@Test
	void accessTokenRoundTrip() {
		String token = provider.generateAccessToken(42L, "USER");

		assertThat(provider.validateToken(token)).isTrue();
		assertThat(provider.getUserId(token)).isEqualTo(42L);
		assertThat(provider.getRole(token)).isEqualTo("USER");
	}

	@Test
	void invalidTokenFailsValidation() {
		assertThat(provider.validateToken("not-a-jwt")).isFalse();
	}
}
