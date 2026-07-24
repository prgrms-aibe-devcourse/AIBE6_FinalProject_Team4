package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartService {

	private final CartItemRepository cartItemRepository;
}
