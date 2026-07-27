package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentStateService {

	private final PaymentRepository paymentRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failPendingPayment(Long paymentId) {
		int updated = paymentRepository.updateStatusIfCurrent(
				paymentId,
				PaymentStatus.PENDING,
				PaymentStatus.FAILED,
				null,
				null
		);
		if (updated == 0) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}
	}
}
