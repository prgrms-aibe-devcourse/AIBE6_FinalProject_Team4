package com.kiwobollae.api.point.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentDirection;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentHistoryResponse;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPointAdjustmentHistoryService {

	private static final int MAX_PAGE_SIZE = 100;

	private final PointTransactionRepository pointTransactionRepository;

	public Page<AdminPointAdjustmentHistoryResponse> getAdjustments(
			Long targetUserId,
			CurrencyType currencyType,
			AdminPointAdjustmentDirection direction,
			LocalDateTime from,
			LocalDateTime to,
			Pageable pageable
	) {
		if (targetUserId != null && targetUserId < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		if (from != null && to != null && !from.isBefore(to)) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}

		Long amountSign = direction == null
				? null
				: direction == AdminPointAdjustmentDirection.GRANT ? 1L : -1L;
		Pageable safePageable = PageRequest.of(
				pageable.getPageNumber(),
				Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
		);
		return pointTransactionRepository.searchAdminAdjustments(
				targetUserId,
				currencyType,
				amountSign,
				from,
				to,
				safePageable
		).map(AdminPointAdjustmentHistoryResponse::from);
	}
}
