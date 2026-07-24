package com.kiwobollae.api.payment.repository;

import com.kiwobollae.api.payment.entity.ChargeProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeProductRepository extends JpaRepository<ChargeProduct, Long> {
}
