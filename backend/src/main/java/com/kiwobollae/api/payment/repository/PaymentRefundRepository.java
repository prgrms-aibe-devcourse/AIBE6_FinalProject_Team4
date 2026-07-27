package com.kiwobollae.api.payment.repository;

import com.kiwobollae.api.payment.entity.PaymentRefund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

	List<PaymentRefund> findAllByPayment_IdOrderByCreatedAtDesc(Long paymentId);
}
