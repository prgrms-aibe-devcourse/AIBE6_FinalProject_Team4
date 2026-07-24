package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrder, Long> {
}
