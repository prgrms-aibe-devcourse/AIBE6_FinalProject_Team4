package com.kiwobollae.api.point.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.PointActivityResponse;
import com.kiwobollae.api.point.dto.response.PointTransactionResponse;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointTransactionService {
	private static final int MAX_PAGE_SIZE = 100;

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

	/** 사용자 화면용 포인트 활동 조회. 같은 거래의 유상·무상 원장은 한 건으로 묶는다. */
	public Page<PointActivityResponse> getActivities(
			Long userId,
			PointTxType type,
			PointRefType refType,
			LocalDateTime from,
			LocalDateTime to,
			Pageable pageable
	) {
		if (from != null && to != null && !from.isBefore(to)) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		Pageable safePageable = PageRequest.of(
				pageable.getPageNumber(),
				Math.min(pageable.getPageSize(), MAX_PAGE_SIZE)
		);
		return pointTransactionRepository.searchActivities(
				wallet.getId(),
				type == null ? null : type.name(),
				refType == null ? null : refType.name(),
				from,
				to,
				safePageable
		).map(PointActivityResponse::from);
	}
}
