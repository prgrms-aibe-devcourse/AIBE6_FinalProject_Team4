package com.kiwobollae.api.point.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.PointTransactionResponse;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointTransactionService {

	private final WalletRepository walletRepository;
	private final PointTransactionRepository pointTransactionRepository;

	/** POINT-02: 내 지갑의 거래 내역 조회(유형/기간 필터 + 페이지네이션). */
	public Page<PointTransactionResponse> getTransactions(Long userId, PointTxType type,
			LocalDateTime from, LocalDateTime to, Pageable pageable) {
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		return pointTransactionRepository.search(wallet.getId(), type, from, to, pageable)
				.map(PointTransactionResponse::from);
	}
}
