package com.kiwobollae.api.infra.repository;

import com.kiwobollae.api.infra.entity.IdempotencyKey;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

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
