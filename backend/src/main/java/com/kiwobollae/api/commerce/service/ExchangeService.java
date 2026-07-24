package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.repository.ExchangeOrderRepository;
import com.kiwobollae.api.commerce.repository.ExchangeProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeService {

	private final ExchangeProductRepository exchangeProductRepository;
	private final ExchangeOrderRepository exchangeOrderRepository;
}
