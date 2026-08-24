package com.kiwobollae.api.global.concurrency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 유니크 제약으로 중복을 막는 "먼저 저장한 쪽만 성공" 패턴(좋아요 중복 방지, IP당 조회 1회
 * 등)에서 유니크 제약 위반을 안전하게 흡수하기 위한 헬퍼.
 *
 * <p>호출부 트랜잭션 안에서 saveAndFlush() 실패를 그냥 try/catch로 잡기만 하면, 자바 코드
 * 상으로는 예외가 처리된 것처럼 보여도 Hibernate 세션이 이미 그 예외로 오염된 상태라 이후
 * 같은 트랜잭션에서 어떤 작업을 하든(또는 그냥 커밋만 해도) 최종 커밋 시점에 실패할 수 있다.
 * 이 시도를 별도의 {@code REQUIRES_NEW} 트랜잭션으로 격리하면, 실패해도 이 트랜잭션 하나만
 * 롤백되고 호출한 쪽의 트랜잭션은 전혀 영향을 받지 않는다.
 *
 * <p>동시에 같은 키로 INSERT가 몰리면 유니크 제약 위반(DataIntegrityViolationException)
 * 대신 락 대기 타임아웃(CannotAcquireLockException, PessimisticLockingFailureException의
 * 하위 타입)이 날 수도 있다 — 어느 쪽이든 "이번엔 내가 못 남겼다"는 뜻이라 동일하게 흡수한다.
 * 이걸 놓치면 호출부(예: 로그인 시 일지 리마인더 기록)가 이 부가 로직 실패로 인해
 * 본 기능까지 통째로 실패하게 된다.
 */
@Component
public class UniqueInsertGuard {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean tryInsert(Runnable insert) {
		try {
			insert.run();
			return true;
		} catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			return false;
		}
	}
}
