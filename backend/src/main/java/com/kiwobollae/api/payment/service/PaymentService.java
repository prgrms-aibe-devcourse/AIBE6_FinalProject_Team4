package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.payment.repository.ChargeProductRepository;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final ChargeProductRepository chargeProductRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentRefundRepository paymentRefundRepository;
}
