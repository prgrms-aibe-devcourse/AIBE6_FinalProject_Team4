package com.kiwobollae.api.point.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointCreditService {

	private final WalletService walletService;
	private final PointTransactionRepository pointTransactionRepository;

	@Transactional
	public void creditPaidPoint(Long userId, Long pointAmount, Long paymentId) {
		if (pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				PointTxType.CHARGE,
				PointRefType.PAYMENT,
				paymentId
		)) {
			throw new BusinessException(ErrorCode.POINT_DUPLICATE_TRANSACTION);
		}

		walletService.applyDelta(
				userId,
				PointTxType.CHARGE,
				CurrencyType.PAID,
				pointAmount,
				PointRefType.PAYMENT,
				paymentId
		);
	}
}
