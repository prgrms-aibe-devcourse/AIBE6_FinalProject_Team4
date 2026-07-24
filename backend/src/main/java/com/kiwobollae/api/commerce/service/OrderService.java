package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.repository.OrderItemRepository;
import com.kiwobollae.api.commerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
}
