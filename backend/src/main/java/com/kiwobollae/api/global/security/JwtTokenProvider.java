package com.kiwobollae.api.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final long accessExpirationMs;
	private final long refreshExpirationMs;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-expiration}") long accessExpirationMs,
			@Value("${jwt.refresh-expiration}") long refreshExpirationMs
	) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessExpirationMs = accessExpirationMs;
		this.refreshExpirationMs = refreshExpirationMs;
	}

	public String generateAccessToken(Long userId, String role) {
		return generateToken(userId, role, accessExpirationMs);
	}

	public String generateRefreshToken(Long userId) {
		return generateToken(userId, null, refreshExpirationMs);
	}

	public long getAccessExpirationMs() {
		return accessExpirationMs;
	}

	public long getRefreshExpirationMs() {
		return refreshExpirationMs;
	}

	private String generateToken(Long userId, String role, long expirationMs) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);

		JwtBuilder builder = Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiration(expiry);
		if (role != null) {
			builder.claim("role", role);
		}
		return builder.signWith(key).compact();
	}

	public boolean validateToken(String token) {
		return checkToken(token) == TokenStatus.VALID;
	}

	/**
	 * Same parsing as {@link #validateToken}, but distinguishes "expired" from
	 * "malformed/tampered/wrong signature" so callers (JwtAuthenticationFilter) can
	 * surface AUTH_TOKEN_EXPIRED vs AUTH_TOKEN_INVALID instead of one generic 401.
	 */
	public TokenStatus checkToken(String token) {
		try {
			parseClaims(token);
			return TokenStatus.VALID;
		} catch (ExpiredJwtException e) {
			return TokenStatus.EXPIRED;
		} catch (JwtException | IllegalArgumentException e) {
			return TokenStatus.INVALID;
		}
	}

	public enum TokenStatus {
		VALID, EXPIRED, INVALID
	}

	public Long getUserId(String token) {
		return Long.valueOf(parseClaims(token).getSubject());
	}

	public String getRole(String token) {
		return parseClaims(token).get("role", String.class);
	}

	private Claims parseClaims(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}
