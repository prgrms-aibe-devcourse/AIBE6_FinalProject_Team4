package com.kiwobollae.api.point.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

	private final WalletRepository walletRepository;
	private final PointTransactionRepository pointTransactionRepository;

	/** POINT-10: 회원가입 트랜잭션에서 지갑 자동 생성(paid=0, free=0). auth 도메인이 호출. */
	@Transactional
	public void createWallet(User user) {
		Wallet wallet = Wallet.builder()
				.user(user)
				.paidPoint(0L)
				.freePoint(0L)
				.build();
		walletRepository.save(wallet);
	}

	/** POINT-01: 잔액 조회. 화면엔 paid+free 합산만 노출(정책 #2). */
	public WalletResponse getWallet(Long userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		return WalletResponse.from(wallet);
	}

	/**
	 * 구매 포인트를 무상 포인트부터 차감하고, 사용 통화별 원장을 같은 트랜잭션에 기록한다.
	 */
	@Transactional
	public PointDeductionResult deductForPurchase(
			Long userId,
			long amount,
			PointRefType refType,
			Long refId
	) {
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));

		if (amount < 1 || wallet.totalBalance() < amount) {
			throw new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE);
		}

		long usedFreePoint = Math.min(Math.max(wallet.getFreePoint(), 0L), amount);
		long usedPaidPoint = amount - usedFreePoint;

		if (usedFreePoint > 0) {
			long balanceAfter = wallet.increaseFreePoint(-usedFreePoint);
			saveTransaction(wallet, CurrencyType.FREE, -usedFreePoint, balanceAfter, refType, refId);
		}
		if (usedPaidPoint > 0) {
			long balanceAfter = wallet.increasePaidPoint(-usedPaidPoint);
			saveTransaction(wallet, CurrencyType.PAID, -usedPaidPoint, balanceAfter, refType, refId);
		}

		return new PointDeductionResult(usedFreePoint, usedPaidPoint, wallet.totalBalance());
	}

	/**
	 * 포인트 증감 공통 프리미티브: 지갑 행을 비관적 락으로 잠근 뒤 한 통화(currency)의 잔액에
	 * 부호 있는 delta를 적용하고, 불변 원장(balance_after 스냅샷)을 같은 트랜잭션에 기록한다.
	 * deduct/credit/reward/clawback 등 상위 흐름(POINT-03~08)이 이 메서드를 조합해 사용한다.
	 *
	 * <p>정책: paid_point는 항상 음수 불가. free_point는 CLAWBACK·ADMIN_ADJUST만 음수(부채)
	 * 허용하고, 그 외(PURCHASE 등)의 무상 차감은 하한 0을 지킨다. 하한 위반 시
	 * {@code POINT_INSUFFICIENT_BALANCE}.
	 */
	@Transactional
	public PointTransaction applyDelta(Long userId, PointTxType type, CurrencyType currency,
			long amount, PointRefType refType, Long refId) {
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));

		long balanceAfter;
		if (currency == CurrencyType.PAID) {
			balanceAfter = wallet.increasePaidPoint(amount);
			if (balanceAfter < 0) {
				throw new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE);
			}
		} else {
			balanceAfter = wallet.increaseFreePoint(amount);
			// CLAWBACK·ADMIN_ADJUST만 free_point 음수(부채) 허용. 그 외(PURCHASE 등)의
			// 무상 차감은 잔액 하한(0)을 지켜야 하므로 부족하면 거절한다.
			if (balanceAfter < 0 && !type.allowsNegativeFree()) {
				throw new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE);
			}
		}

		PointTransaction tx = PointTransaction.builder()
				.wallet(wallet)
				.type(type)
				.currencyType(currency)
				.amount(amount)
				.balanceAfter(balanceAfter)
				.refType(refType)
				.refId(refId)
				.build();
		return pointTransactionRepository.save(tx);
	}

	private void saveTransaction(
			Wallet wallet,
			CurrencyType currency,
			long amount,
			long balanceAfter,
			PointRefType refType,
			Long refId
	) {
		pointTransactionRepository.save(PointTransaction.builder()
				.wallet(wallet)
				.type(PointTxType.PURCHASE)
				.currencyType(currency)
				.amount(amount)
				.balanceAfter(balanceAfter)
				.refType(refType)
				.refId(refId)
				.build());
	}
}
