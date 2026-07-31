package com.kiwobollae.api.payment.entity.enums;

/**
 * 환불 시도 기록의 상태.
 *
 * <p>{@code STARTED}로 남아 있는 기록은 "PG에 환불을 요청했지만 우리 쪽 결과가 확정되지 않았다"는
 * 뜻이다 — 사람이 PG 상태를 확인해야 하는 대조 대상이다. 정상적으로 끝난 환불만 {@code SETTLED}가
 * 된다.
 */
public enum PaymentRefundAttemptStatus {
	STARTED, SETTLED
}
