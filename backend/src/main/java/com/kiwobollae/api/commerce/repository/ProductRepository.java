package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
