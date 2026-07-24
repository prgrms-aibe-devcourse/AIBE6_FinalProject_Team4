package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
