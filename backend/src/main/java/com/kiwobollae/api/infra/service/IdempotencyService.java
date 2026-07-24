package com.kiwobollae.api.infra.service;

import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private final IdempotencyKeyRepository idempotencyKeyRepository;
}
