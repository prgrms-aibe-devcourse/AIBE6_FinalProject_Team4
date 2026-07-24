package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
