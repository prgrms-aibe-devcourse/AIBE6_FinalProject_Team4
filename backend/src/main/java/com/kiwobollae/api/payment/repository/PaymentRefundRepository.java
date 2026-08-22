package com.kiwobollae.api.payment.repository;

import com.kiwobollae.api.payment.entity.PaymentRefund;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

	List<PaymentRefund> findAllByPayment_IdOrderByCreatedAtDesc(Long paymentId);

	@EntityGraph(attributePaths = {"payment"})
	@Query("SELECT r FROM PaymentRefund r WHERE r.id = :refundId")
	Optional<PaymentRefund> findDetailsById(@Param("refundId") Long refundId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE PaymentRefund r
			SET r.status = :targetStatus,
			    r.refundKey = :refundKey,
			    r.completedAt = :completedAt
			WHERE r.id = :refundId
			  AND r.status = :expectedStatus
			""")
	int completeIfCurrent(
			@Param("refundId") Long refundId,
			@Param("expectedStatus") PaymentRefundStatus expectedStatus,
			@Param("targetStatus") PaymentRefundStatus targetStatus,
			@Param("refundKey") String refundKey,
			@Param("completedAt") LocalDateTime completedAt
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE PaymentRefund r
			SET r.status = :targetStatus,
			    r.completedAt = :completedAt
			WHERE r.id = :refundId
			  AND r.status = :expectedStatus
			""")
	int failIfCurrent(
			@Param("refundId") Long refundId,
			@Param("expectedStatus") PaymentRefundStatus expectedStatus,
			@Param("targetStatus") PaymentRefundStatus targetStatus,
			@Param("completedAt") LocalDateTime completedAt
	);
}
