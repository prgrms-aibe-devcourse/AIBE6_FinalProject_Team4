package com.kiwobollae.api.payment.repository;

import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	@EntityGraph(attributePaths = {"user", "chargeProduct"})
	@Query("SELECT p FROM Payment p WHERE p.providerOrderId = :providerOrderId AND p.user.id = :userId")
	Optional<Payment> findDetailsByProviderOrderIdAndUserId(
			@Param("providerOrderId") String providerOrderId,
			@Param("userId") Long userId
	);

	@EntityGraph(attributePaths = {"user", "chargeProduct"})
	@Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
	Optional<Payment> findDetailsById(@Param("paymentId") Long paymentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {"user", "chargeProduct"})
	@Query("""
			SELECT p
			FROM Payment p
			WHERE p.id = :paymentId
			  AND p.user.id = :userId
			""")
	Optional<Payment> findDetailsByIdAndUserIdForUpdate(
			@Param("paymentId") Long paymentId,
			@Param("userId") Long userId
	);

	@EntityGraph(attributePaths = {"user", "chargeProduct"})
	List<Payment> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Payment p
			SET p.status = :targetStatus,
			    p.providerPaymentKey = :paymentKey,
			    p.approvedAt = :approvedAt
			WHERE p.id = :paymentId
			  AND p.status = :expectedStatus
			""")
	int updateStatusIfCurrent(
			@Param("paymentId") Long paymentId,
			@Param("expectedStatus") PaymentStatus expectedStatus,
			@Param("targetStatus") PaymentStatus targetStatus,
			@Param("paymentKey") String paymentKey,
			@Param("approvedAt") LocalDateTime approvedAt
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Payment p
			SET p.status = :targetStatus
			WHERE p.id = :paymentId
			  AND p.status = :expectedStatus
			""")
	int updateStatusOnlyIfCurrent(
			@Param("paymentId") Long paymentId,
			@Param("expectedStatus") PaymentStatus expectedStatus,
			@Param("targetStatus") PaymentStatus targetStatus
	);
}
