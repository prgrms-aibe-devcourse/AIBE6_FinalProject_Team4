package com.kiwobollae.api.payment.repository;

import com.kiwobollae.api.payment.entity.PaymentRefundAttempt;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundAttemptStatus;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRefundAttemptRepository extends JpaRepository<PaymentRefundAttempt, Long> {

	// 결과가 확정되지 않은 시도가 남아 있는지 확인한다. 남아 있으면 PG 처리 결과를 알 수 없는
	// 상태라, 자동 재환불(=이중 환불 위험) 대신 사람이 확인하도록 막는다.
	boolean existsByPaymentIdAndStatus(Long paymentId, PaymentRefundAttemptStatus status);

	// 성공 또는 명확한 거절의 내부 처리가 모두 끝났을 때 최종 트랜잭션에서 SETTLED로 바꾼다.
	// 최종 트랜잭션이 롤백되면 STARTED로 남아 위 확인에 걸린다.
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE PaymentRefundAttempt a
			SET a.status = :targetStatus,
			    a.settledAt = :settledAt
			WHERE a.id = :attemptId
			  AND a.status = :expectedStatus
			""")
	int settleIfCurrent(
			@Param("attemptId") Long attemptId,
			@Param("expectedStatus") PaymentRefundAttemptStatus expectedStatus,
			@Param("targetStatus") PaymentRefundAttemptStatus targetStatus,
			@Param("settledAt") LocalDateTime settledAt
	);
}
