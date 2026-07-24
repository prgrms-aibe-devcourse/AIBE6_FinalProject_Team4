package com.kiwobollae.api.payment.repository;

import com.kiwobollae.api.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
