package com.kiwobollae.api.infra.service;

import com.kiwobollae.api.infra.entity.IdempotencyKey;

public record IdempotencyExecution(
		IdempotencyKey key,
		boolean replay
) {
}
