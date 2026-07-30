package com.kiwobollae.api.point.repository;

import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

	boolean existsByTypeAndRefTypeAndRefId(PointTxType type, PointRefType refType, Long refId);

	List<PointTransaction> findAllByWalletAndTypeAndRefTypeAndRefId(
			Wallet wallet,
			PointTxType type,
			PointRefType refType,
			Long refId
	);

	/** 지갑별 원장 조회. type/기간은 null이면 미적용(선택 필터). 정렬·페이지는 Pageable로. */
	@Query("""
			SELECT pt FROM PointTransaction pt
			WHERE pt.wallet.id = :walletId
			  AND (:type IS NULL OR pt.type = :type)
			  AND (:from IS NULL OR pt.createdAt >= :from)
			  AND (:to IS NULL OR pt.createdAt < :to)
			""")
	Page<PointTransaction> search(@Param("walletId") Long walletId,
			@Param("type") PointTxType type,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			Pageable pageable);
}
