package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;
}
