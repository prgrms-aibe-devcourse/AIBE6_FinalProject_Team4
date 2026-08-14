package com.kiwobollae.api.infra.repository;

import com.kiwobollae.api.infra.entity.IdempotencyKey;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

	/**
	 * MySQL unique index를 이용해 비어 있는 멱등키를 원자적으로 선점한다. duplicate key만
	 * no-op 처리하고 FK·NOT NULL·길이 오류는 그대로 실패시킨다. claimToken으로 이번 요청이
	 * 실제 삽입자인지 판별하므로 JDBC affected-row 설정에 의존하지 않는다.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO idempotency_keys (
			    user_id, api_type, client_key, request_hash, claim_token,
			    status, expires_at, created_at
			) VALUES (
			    :userId, :apiType, :clientKey, :requestHash, :claimToken,
			    'IN_PROGRESS', :expiresAt, :createdAt
			)
			ON DUPLICATE KEY UPDATE id = id
			""", nativeQuery = true)
	int claim(
			@Param("userId") Long userId,
			@Param("apiType") String apiType,
			@Param("clientKey") String clientKey,
			@Param("requestHash") String requestHash,
			@Param("claimToken") String claimToken,
			@Param("expiresAt") java.time.LocalDateTime expiresAt,
			@Param("createdAt") java.time.LocalDateTime createdAt
	);

	Optional<IdempotencyKey> findByUser_IdAndApiTypeAndClientKey(
			Long userId,
			String apiType,
			String clientKey
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT k
			FROM IdempotencyKey k
			WHERE k.user.id = :userId
			  AND k.apiType = :apiType
			  AND k.clientKey = :clientKey
			""")
	Optional<IdempotencyKey> findForUpdate(
			@Param("userId") Long userId,
			@Param("apiType") String apiType,
			@Param("clientKey") String clientKey
	);
}
