package com.kiwobollae.api.infra.repository;

import com.kiwobollae.api.infra.entity.IdempotencyKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

	Optional<IdempotencyKey> findByUser_IdAndApiTypeAndClientKey(
			Long userId,
			String apiType,
			String clientKey
	);
}
