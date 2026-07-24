package com.kiwobollae.api.payment.repository;

import com.kiwobollae.api.payment.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
}
