package com.kiwobollae.api.point.repository;

import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.projection.PointActivityProjection;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

	boolean existsByTypeAndRefTypeAndRefId(PointTxType type, PointRefType refType, Long refId);

	/** 로컬 시나리오 시드의 일지 보상 표시 시각을 해당 과거 일지 날짜로 맞춘다. */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE point_transactions
			SET created_at = :createdAt
			WHERE type = 'JOURNAL_REWARD'
			  AND ref_type = 'JOURNAL_COMPLETION'
			  AND ref_id = :journalId
			""", nativeQuery = true)
	int backdateLocalSeedJournalReward(
			@Param("journalId") Long journalId,
			@Param("createdAt") LocalDateTime createdAt
	);

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

	@Query(
			value = """
					SELECT MAX(pt.id) AS id,
					       pt.type AS type,
					       pt.ref_type AS refType,
					       pt.ref_id AS refId,
					       CAST(SUM(pt.amount) AS SIGNED) AS amount,
					       CAST(SUM(CASE WHEN pt.currency_type = 'PAID' THEN pt.amount ELSE 0 END) AS SIGNED) AS paidAmount,
					       CAST(SUM(CASE WHEN pt.currency_type = 'FREE' THEN pt.amount ELSE 0 END) AS SIGNED) AS freeAmount,
					       MAX(CASE WHEN pt.currency_type = 'PAID' THEN pt.balance_after END) AS paidBalanceAfter,
					       MAX(CASE WHEN pt.currency_type = 'FREE' THEN pt.balance_after END) AS freeBalanceAfter,
					       MAX(pt.created_at) AS createdAt
					FROM point_transactions pt
					WHERE pt.wallet_id = :walletId
					  AND (:type IS NULL OR pt.type = :type)
					  AND (:refType IS NULL OR pt.ref_type = :refType)
					  AND (:from IS NULL OR pt.created_at >= :from)
					  AND (:to IS NULL OR pt.created_at < :to)
					GROUP BY pt.type,
					         pt.ref_type,
					         pt.ref_id,
					         CASE
					             WHEN pt.ref_id IS NULL OR pt.type = 'ADMIN_ADJUST' THEN pt.id
					             ELSE 0
					         END
					ORDER BY createdAt DESC, id DESC
					""",
			countQuery = """
					SELECT COUNT(*)
					FROM (
					    SELECT 1
					    FROM point_transactions pt
					    WHERE pt.wallet_id = :walletId
					      AND (:type IS NULL OR pt.type = :type)
					      AND (:refType IS NULL OR pt.ref_type = :refType)
					      AND (:from IS NULL OR pt.created_at >= :from)
					      AND (:to IS NULL OR pt.created_at < :to)
					    GROUP BY pt.type,
					             pt.ref_type,
					             pt.ref_id,
					             CASE
					                 WHEN pt.ref_id IS NULL OR pt.type = 'ADMIN_ADJUST' THEN pt.id
					                 ELSE 0
					             END
					) grouped_activity
					""",
			nativeQuery = true
	)
	Page<PointActivityProjection> searchActivities(
			@Param("walletId") Long walletId,
			@Param("type") String type,
			@Param("refType") String refType,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			Pageable pageable
	);

	@Query(
			value = """
					SELECT pt FROM PointTransaction pt
					JOIN FETCH pt.wallet wallet
					JOIN FETCH wallet.user targetUser
					WHERE pt.type = com.kiwobollae.api.point.entity.enums.PointTxType.ADMIN_ADJUST
					  AND pt.refType = com.kiwobollae.api.point.entity.enums.PointRefType.ADMIN
					  AND (:targetUserId IS NULL OR targetUser.id = :targetUserId)
					  AND (:currencyType IS NULL OR pt.currencyType = :currencyType)
					  AND (:amountSign IS NULL
					       OR (:amountSign > 0 AND pt.amount > 0)
					       OR (:amountSign < 0 AND pt.amount < 0))
					  AND (:from IS NULL OR pt.createdAt >= :from)
					  AND (:to IS NULL OR pt.createdAt < :to)
					""",
			countQuery = """
					SELECT COUNT(pt) FROM PointTransaction pt
					JOIN pt.wallet wallet
					JOIN wallet.user targetUser
					WHERE pt.type = com.kiwobollae.api.point.entity.enums.PointTxType.ADMIN_ADJUST
					  AND pt.refType = com.kiwobollae.api.point.entity.enums.PointRefType.ADMIN
					  AND (:targetUserId IS NULL OR targetUser.id = :targetUserId)
					  AND (:currencyType IS NULL OR pt.currencyType = :currencyType)
					  AND (:amountSign IS NULL
					       OR (:amountSign > 0 AND pt.amount > 0)
					       OR (:amountSign < 0 AND pt.amount < 0))
					  AND (:from IS NULL OR pt.createdAt >= :from)
					  AND (:to IS NULL OR pt.createdAt < :to)
					"""
	)
	Page<PointTransaction> searchAdminAdjustments(
			@Param("targetUserId") Long targetUserId,
			@Param("currencyType") CurrencyType currencyType,
			@Param("amountSign") Long amountSign,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			Pageable pageable
	);
}
