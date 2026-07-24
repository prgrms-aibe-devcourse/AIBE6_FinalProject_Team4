package com.kiwobollae.api.infra.repository;

import com.kiwobollae.api.infra.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
}
