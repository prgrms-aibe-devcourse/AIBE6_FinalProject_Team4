package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
