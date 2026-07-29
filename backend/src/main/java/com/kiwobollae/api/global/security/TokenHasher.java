package com.kiwobollae.api.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Hashes raw refresh token strings for storage/lookup in {@code refresh_token.token_hash}.
 * SHA-256 (deterministic) is used instead of BCrypt because the token must be
 * looked up by exact hash match, not verified one-by-one.
 */
@Component
public class TokenHasher {

	public String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(hashBytes.length * 2);
			for (byte b : hashBytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
		}
	}
}
