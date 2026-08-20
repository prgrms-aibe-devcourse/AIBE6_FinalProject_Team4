package com.kiwobollae.api.global.cache;

import com.kiwobollae.api.auth.entity.enums.UserStatus;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * JwtAuthenticationFilter가 매 요청마다 DB에 날리던 계정 상태(ACTIVE 여부) 재확인을
 * 짧은 TTL로 캐싱해 DB 왕복을 줄인다. 관리자가 계정을 정지/해제하는 순간 evict를 호출해
 * 캐시를 즉시 비우므로, "즉시 반영"이라는 원래 요구사항은 그대로 유지된다.
 *
 * <p>Redis 자체가 죽어있거나 응답하지 않아도 인증 흐름이 막히면 안 되므로, 모든 메서드가
 * 예외를 삼키고 "캐시 미스"처럼 동작한다 — 호출부(JwtAuthenticationFilter)는 이 경우
 * 그냥 DB로 폴백한다.
 */
@Slf4j
@Component
public class UserStatusCache {

	private static final String KEY_PREFIX = "user:status:";
	private static final Duration TTL = Duration.ofSeconds(30);

	private final StringRedisTemplate redisTemplate;

	public UserStatusCache(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public Optional<UserStatus> get(Long userId) {
		try {
			String value = redisTemplate.opsForValue().get(key(userId));
			return value == null ? Optional.empty() : Optional.of(UserStatus.valueOf(value));
		} catch (Exception e) {
			log.warn("UserStatusCache read failed for userId={}, falling back to DB", userId, e);
			return Optional.empty();
		}
	}

	public void put(Long userId, UserStatus status) {
		try {
			redisTemplate.opsForValue().set(key(userId), status.name(), TTL);
		} catch (Exception e) {
			log.warn("UserStatusCache write failed for userId={}", userId, e);
		}
	}

	public void evict(Long userId) {
		try {
			redisTemplate.delete(key(userId));
		} catch (Exception e) {
			log.warn("UserStatusCache evict failed for userId={}", userId, e);
		}
	}

	private String key(Long userId) {
		return KEY_PREFIX + userId;
	}
}
