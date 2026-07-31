package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.payment.entity.PaymentRefundAttempt;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundAttemptStatus;
import com.kiwobollae.api.payment.repository.PaymentRefundAttemptRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 시도 기록을 본 환불 트랜잭션과 분리해 커밋한다.
 *
 * <p>{@link PaymentRefundService#refund}는 하나의 트랜잭션이라, PG 환불이 성공한 뒤 상태 전이나
 * 커밋이 실패하면 payment_refunds의 REQUESTED 행까지 함께 롤백된다. 현금·포인트는 이미 나갔는데
 * 남는 기록이 없어 대조하지 않으면 아무도 모르는 상태가 된다. 그래서 PG를 호출하기 직전에 이
 * 서비스로 시도 기록을 별도 트랜잭션에서 커밋한다.
 *
 * <p>같은 클래스 안에서 호출하면 프록시를 우회해 REQUIRES_NEW가 적용되지 않으므로 별도 빈으로
 * 분리했다({@link PaymentStateService}와 같은 이유).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundAttemptService {

	private final PaymentRefundAttemptRepository paymentRefundAttemptRepository;
	private final Clock seoulClock;

	/**
	 * STARTED 시도 기록을 즉시 커밋하고 그 id를 반환한다. 호출부 트랜잭션이 롤백되어도 이 행은 남는다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Long start(Long paymentId, Long userId, Long cashAmount, Long pointAmount, String reason) {
		PaymentRefundAttempt attempt = paymentRefundAttemptRepository.saveAndFlush(
				PaymentRefundAttempt.builder()
						.paymentId(paymentId)
						.userId(userId)
						.cashAmount(cashAmount)
						.pointAmount(pointAmount)
						.status(PaymentRefundAttemptStatus.STARTED)
						.reason(reason)
						.startedAt(LocalDateTime.now(seoulClock))
						.build()
		);
		log.info("환불 시도 시작: attemptId={}, paymentId={}, cash={}, point={}",
				attempt.getId(), paymentId, cashAmount, pointAmount);
		return attempt.getId();
	}
}
