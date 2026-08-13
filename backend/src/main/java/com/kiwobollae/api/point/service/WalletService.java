package com.kiwobollae.api.point.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.dto.response.JournalRewardResult;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.AdminPointAdjustmentReason;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

	private static final long JOURNAL_REWARD_AMOUNT = 100L;
	private static final long ORDER_FREE_POINT_UNIT = 100L;

	private final WalletRepository walletRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final PointTransactionTimeProvider pointTransactionTimeProvider;

	/** POINT-10: 일반·소셜 회원가입 트랜잭션에서 지갑 자동 생성(paid=0, free=0). auth 도메인이 호출. */
	@Transactional
	public void createWallet(User user) {
		Wallet wallet = Wallet.builder()
				.user(user)
				.paidPoint(0L)
				.freePoint(0L)
				.build();
		walletRepository.save(wallet);
	}

	/** POINT-01: 합산 잔액과 유상/무상 잔액을 조회한다. */
	public WalletResponse getWallet(Long userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		return WalletResponse.from(wallet);
	}

	/** 관리자가 특정 사용자의 유상/무상 포인트를 조정하고 불변 원장을 기록한다. */
	@Transactional
	public AdminPointAdjustmentResponse adjustByAdmin(
			Long adminUserId,
			Long userId,
			CurrencyType currency,
			long amount,
			AdminPointAdjustmentReason adjustmentReason
	) {
		if (adminUserId == null || adminUserId < 1
				|| userId == null || userId < 1 || currency == null || amount == 0
				|| adjustmentReason == null || !adjustmentReason.supports(amount)) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		if (adminUserId.equals(userId)) {
			throw new BusinessException(ErrorCode.POINT_SELF_ADJUSTMENT_FORBIDDEN);
		}
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		PointTransaction transaction = applyDeltaToWallet(
				wallet,
				PointTxType.ADMIN_ADJUST,
				currency,
				amount,
				PointRefType.ADMIN,
				adminUserId,
				adjustmentReason
		);
		return AdminPointAdjustmentResponse.from(userId, transaction, wallet);
	}

	/**
	 * 성장 일지 작성 보상으로 무상 포인트 100P를 한 번만 지급한다.
	 *
	 * <p>호출 중인 트랜잭션이 있으면 반드시 참여한다. 일지 보상 판정·카드팩·알림과 함께 처리하는 흐름에서
	 * 포인트 원장만 별도로 커밋되지 않게 하기 위함이다.
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public JournalRewardResult rewardJournal(Long userId, Long journalId) {
		validateJournalRewardRequest(userId, journalId);
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));

		if (hasJournalTransaction(
				PointTxType.JOURNAL_REWARD,
				PointRefType.JOURNAL_COMPLETION,
				journalId
		)) {
			throw new BusinessException(ErrorCode.POINT_DUPLICATE_TRANSACTION);
		}

		long balanceAfter = wallet.increaseFreePoint(JOURNAL_REWARD_AMOUNT);
		saveTransaction(
				wallet,
				PointTxType.JOURNAL_REWARD,
				CurrencyType.FREE,
				JOURNAL_REWARD_AMOUNT,
				balanceAfter,
				PointRefType.JOURNAL_COMPLETION,
				journalId
		);
		return new JournalRewardResult(JOURNAL_REWARD_AMOUNT);
	}

	/**
	 * 기존 구매 도메인 연동을 위한 호환 메서드다.
	 *
	 * <p>카드 구매는 무상 포인트를 먼저 사용하고 부족분을 유상 포인트로 차감한다. 상품 주문은
	 * 무상 포인트를 요청하지 않은 기본값(유상 포인트만 사용)으로 처리한다. 새 상품 주문 흐름은
	 * 무상 포인트 사용액을 명시할 수 있는
	 * {@link #deductForOrderPurchase(Long, long, long, Long)}를 사용한다.
	 */
	@Transactional
	public PointDeductionResult deductForPurchase(
			Long userId,
			long amount,
			PointRefType refType,
			Long refId
	) {
		if (refType == PointRefType.CARD_PURCHASE) {
			return deductForCardPurchase(userId, amount, refId);
		}
		if (refType == PointRefType.ORDER) {
			return deductForOrderPurchase(userId, amount, 0L, refId);
		}
		throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
	}

	/** 카드 구매 금액을 무상 포인트에서 먼저 차감하고 부족분을 유상 포인트에서 차감한다. */
	@Transactional
	public PointDeductionResult deductForCardPurchase(
			Long userId,
			long amount,
			Long cardPurchaseId
	) {
		validatePurchaseRequest(amount, PointRefType.CARD_PURCHASE, cardPurchaseId);
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));

		long availableFreePoint = Math.max(wallet.getFreePoint(), 0L);
		long usedFreePoint = Math.min(availableFreePoint, amount);
		long usedPaidPoint = amount - usedFreePoint;
		if (wallet.getPaidPoint() < usedPaidPoint) {
			throw new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE);
		}

		return deductPoints(
				wallet,
				usedFreePoint,
				usedPaidPoint,
				PointRefType.CARD_PURCHASE,
				cardPurchaseId
		);
	}

	/** 가챠 팩 구매 금액을 무상 포인트에서 먼저 차감하고 부족분을 유상 포인트에서 차감한다. */
	@Transactional
	public PointDeductionResult deductForGachaPurchase(
			Long userId,
			long amount,
			Long gachaPurchaseId
	) {
		validatePurchaseRequest(amount, PointRefType.GACHA_PURCHASE, gachaPurchaseId);
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));

		long availableFreePoint = Math.max(wallet.getFreePoint(), 0L);
		long usedFreePoint = Math.min(availableFreePoint, amount);
		long usedPaidPoint = amount - usedFreePoint;
		if (wallet.getPaidPoint() < usedPaidPoint) {
			throw new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE);
		}

		return deductPoints(
				wallet,
				usedFreePoint,
				usedPaidPoint,
				PointRefType.GACHA_PURCHASE,
				gachaPurchaseId
		);
	}

	/**
	 * 상품 주문에 요청한 무상 포인트를 100P 단위로 차감하고, 나머지 금액을 유상 포인트로
	 * 차감한다. requestedFreePoint가 0이면 유상 포인트만 사용한다.
	 */
	@Transactional
	public PointDeductionResult deductForOrderPurchase(
			Long userId,
			long amount,
			long requestedFreePoint,
			Long orderId
	) {
		// products.point_price는 0 이상을 허용하므로(docs/AGENTS.md 5), 담긴 상품이 전부 0P인
		// 주문은 amount==0이 될 수 있다. 카드 구매(항상 유상)와 달리 여기서는 0을 정상 거래로
		// 취급해야 하므로 공용 validatePurchaseRequest(amount<1 거부)를 쓰지 않는다.
		if (amount < 0 || orderId == null || orderId < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		if (requestedFreePoint < 0
				|| requestedFreePoint > amount
				|| requestedFreePoint % ORDER_FREE_POINT_UNIT != 0) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}

		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		long usedPaidPoint = amount - requestedFreePoint;

		if (Math.max(wallet.getFreePoint(), 0L) < requestedFreePoint
				|| wallet.getPaidPoint() < usedPaidPoint) {
			throw new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE);
		}

		return deductPoints(
				wallet,
				requestedFreePoint,
				usedPaidPoint,
				PointRefType.ORDER,
				orderId
		);
	}

	/**
	 * 구매 원장과 호출자가 전달한 통화별 사용액이 일치할 때만 그대로 원복한다.
	 * 동일 구매 건은 한 번만 원복할 수 있다.
	 */
	@Transactional
	public void restorePurchasePoints(
			Long userId,
			long usedFreePoint,
			long usedPaidPoint,
			PointRefType refType,
			Long refId
	) {
		validateRestoreRequest(usedFreePoint, usedPaidPoint, refType, refId);
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));

		if (pointTransactionRepository.existsByTypeAndRefTypeAndRefId(PointTxType.RESTORE, refType, refId)) {
			throw new BusinessException(ErrorCode.POINT_DUPLICATE_TRANSACTION);
		}

		PurchasePointUsage recordedUsage = getRecordedPurchaseUsage(wallet, refType, refId);
		if (recordedUsage.freePoint() != usedFreePoint
				|| recordedUsage.paidPoint() != usedPaidPoint) {
			throw purchaseUsageMismatch(recordedUsage, usedFreePoint, usedPaidPoint);
		}

		restoreRecordedPoints(wallet, recordedUsage, refType, refId);
	}

	/** 가챠 구매 원장을 정본으로 유상·무상 포인트를 정확히 한 번 원복한다. */
	@Transactional
	public void restoreGachaPurchasePoints(Long userId, Long gachaPurchaseId) {
		if (userId == null || userId < 1 || gachaPurchaseId == null || gachaPurchaseId < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		if (pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				PointTxType.RESTORE, PointRefType.GACHA_PURCHASE, gachaPurchaseId)) {
			return;
		}

		PurchasePointUsage recordedUsage =
				getRecordedPurchaseUsage(wallet, PointRefType.GACHA_PURCHASE, gachaPurchaseId);
		if (recordedUsage.freePoint() == 0 && recordedUsage.paidPoint() == 0) {
			throw new BusinessException(
					ErrorCode.COMMON_DATA_CONFLICT, "가챠 구매 차감 원장을 찾을 수 없습니다.");
		}
		restoreRecordedPoints(
				wallet, recordedUsage, PointRefType.GACHA_PURCHASE, gachaPurchaseId);
	}

	/**
	 * 충전 결제 전액 환불을 위해 유상 포인트만 회수한다.
	 * 무상 포인트는 사용하지 않으며, 환불 건별 원장을 한 번만 기록한다.
	 */
	@Transactional
	public void deductPaidPointForPaymentRefund(
			Long userId,
			long pointAmount,
			Long paymentRefundId
	) {
		if (pointAmount < 1 || paymentRefundId == null || paymentRefundId < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}

		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		if (pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				PointTxType.REFUND,
				PointRefType.PAYMENT_REFUND,
				paymentRefundId
		)) {
			throw new BusinessException(ErrorCode.POINT_DUPLICATE_TRANSACTION);
		}
		if (wallet.getPaidPoint() < pointAmount) {
			throw new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE);
		}

		long balanceAfter = wallet.increasePaidPoint(-pointAmount);
		saveTransaction(
				wallet,
				PointTxType.REFUND,
				CurrencyType.PAID,
				-pointAmount,
				balanceAfter,
				PointRefType.PAYMENT_REFUND,
				paymentRefundId
		);
	}

	/**
	 * 포인트 증감 공통 프리미티브: 지갑 행을 비관적 락으로 잠근 뒤 한 통화(currency)의 잔액에
	 * 부호 있는 delta를 적용하고, 불변 원장(balance_after 스냅샷)을 같은 트랜잭션에 기록한다.
	 * deduct/credit/reward 등 상위 흐름이 이 메서드를 조합해 사용한다.
	 * 관리자 조정은 사유 검증과 저장을 보장하는 {@link #adjustByAdmin}만 사용한다.
	 *
	 * <p>정책: paid_point와 free_point 모두 음수가 될 수 없다. 하한 위반 시
	 * {@code POINT_INSUFFICIENT_BALANCE}를 반환한다.
	 */
	@Transactional
	public PointTransaction applyDelta(Long userId, PointTxType type, CurrencyType currency,
			long amount, PointRefType refType, Long refId) {
		if (userId == null || userId < 1 || type == null || type == PointTxType.ADMIN_ADJUST
				|| currency == null || amount == 0) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POINT_WALLET_NOT_FOUND));
		return applyDeltaToWallet(wallet, type, currency, amount, refType, refId, null);
	}

	private PointTransaction applyDeltaToWallet(
			Wallet wallet,
			PointTxType type,
			CurrencyType currency,
			long amount,
			PointRefType refType,
			Long refId,
			AdminPointAdjustmentReason adjustmentReason
	) {
		long currentBalance = currency == CurrencyType.PAID
				? wallet.getPaidPoint()
				: wallet.getFreePoint();
		long balanceAfter;
		try {
			balanceAfter = Math.addExact(currentBalance, amount);
		} catch (ArithmeticException exception) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "포인트 조정 금액이 허용 범위를 벗어났습니다.");
		}
		if (balanceAfter < 0) {
			throw new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE);
		}

		if (currency == CurrencyType.PAID) {
			wallet.increasePaidPoint(amount);
		} else {
			wallet.increaseFreePoint(amount);
		}

		PointTransaction tx = PointTransaction.builder()
				.wallet(wallet)
				.type(type)
				.currencyType(currency)
				.amount(amount)
				.balanceAfter(balanceAfter)
				.refType(refType)
				.refId(refId)
				.adjustmentReason(adjustmentReason)
				.createdAt(pointTransactionTimeProvider.now())
				.build();
		return pointTransactionRepository.save(tx);
	}

	private void saveTransaction(
			Wallet wallet,
			PointTxType type,
			CurrencyType currency,
			long amount,
			long balanceAfter,
			PointRefType refType,
			Long refId
	) {
		pointTransactionRepository.save(PointTransaction.builder()
				.wallet(wallet)
				.type(type)
				.currencyType(currency)
				.amount(amount)
				.balanceAfter(balanceAfter)
				.refType(refType)
				.refId(refId)
				.createdAt(pointTransactionTimeProvider.now())
				.build());
	}

	private void restoreRecordedPoints(
			Wallet wallet,
			PurchasePointUsage recordedUsage,
			PointRefType refType,
			Long refId
	) {
		if (recordedUsage.freePoint() > 0) {
			long balanceAfter = wallet.increaseFreePoint(recordedUsage.freePoint());
			saveTransaction(
					wallet, PointTxType.RESTORE, CurrencyType.FREE,
					recordedUsage.freePoint(), balanceAfter, refType, refId);
		}
		if (recordedUsage.paidPoint() > 0) {
			long balanceAfter = wallet.increasePaidPoint(recordedUsage.paidPoint());
			saveTransaction(
					wallet, PointTxType.RESTORE, CurrencyType.PAID,
					recordedUsage.paidPoint(), balanceAfter, refType, refId);
		}
	}

	private PointDeductionResult deductPoints(
			Wallet wallet,
			long usedFreePoint,
			long usedPaidPoint,
			PointRefType refType,
			Long refId
	) {
		if (usedFreePoint > 0) {
			long balanceAfter = wallet.increaseFreePoint(-usedFreePoint);
			saveTransaction(
					wallet,
					PointTxType.PURCHASE,
					CurrencyType.FREE,
					-usedFreePoint,
					balanceAfter,
					refType,
					refId
			);
		}
		if (usedPaidPoint > 0) {
			long balanceAfter = wallet.increasePaidPoint(-usedPaidPoint);
			saveTransaction(
					wallet,
					PointTxType.PURCHASE,
					CurrencyType.PAID,
					-usedPaidPoint,
					balanceAfter,
					refType,
					refId
			);
		}

		return new PointDeductionResult(usedFreePoint, usedPaidPoint, wallet.totalBalance());
	}

	private void validatePurchaseRequest(
			long amount,
			PointRefType refType,
			Long refId
	) {
		if (amount < 1 || refType == null || refId == null || refId < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	// 카드는 항상 유상이라 (0,0) 원복 요청 자체가 의미 없는 입력이지만, 상품 주문은
	// point_price==0인 상품만 담겼을 수 있어(docs/AGENTS.md 5) usedFreePoint==usedPaidPoint==0도
	// 유효한 취소일 수 있다. 실제 방어는 recordedUsage(최초 구매 차감 원장)와 정확히
	// 일치하는지 대조하는 쪽이 맡는다.
	private void validateRestoreRequest(
			long usedFreePoint,
			long usedPaidPoint,
			PointRefType refType,
			Long refId
	) {
		if (usedFreePoint < 0 || usedPaidPoint < 0
				|| (refType != PointRefType.ORDER && refType != PointRefType.CARD_PURCHASE)
				|| refId == null || refId < 1
				|| (refType == PointRefType.CARD_PURCHASE && usedFreePoint == 0 && usedPaidPoint == 0)) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private void validateJournalRewardRequest(Long userId, Long journalId) {
		if (userId == null || userId < 1 || journalId == null || journalId < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private boolean hasJournalTransaction(
			PointTxType type,
			PointRefType refType,
			Long journalId
	) {
		return pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				type,
				refType,
				journalId
		);
	}

	private PurchasePointUsage getRecordedPurchaseUsage(
			Wallet wallet,
			PointRefType refType,
			Long refId
	) {
		List<PointTransaction> purchaseTransactions =
				pointTransactionRepository.findAllByWalletAndTypeAndRefTypeAndRefId(
						wallet,
						PointTxType.PURCHASE,
						refType,
						refId
				);

		long recordedFreePoint = 0L;
		long recordedPaidPoint = 0L;
		for (PointTransaction transaction : purchaseTransactions) {
			if (transaction.getAmount() >= 0) {
				throw new BusinessException(
						ErrorCode.COMMON_DATA_CONFLICT,
						"기존 포인트 결제 내역을 확인할 수 없습니다."
				);
			}

			long usedPoint = Math.negateExact(transaction.getAmount());
			if (transaction.getCurrencyType() == CurrencyType.FREE) {
				recordedFreePoint = Math.addExact(recordedFreePoint, usedPoint);
			} else {
				recordedPaidPoint = Math.addExact(recordedPaidPoint, usedPoint);
			}
		}

		return new PurchasePointUsage(recordedFreePoint, recordedPaidPoint);
	}

	private BusinessException purchaseUsageMismatch(
			PurchasePointUsage recordedUsage,
			long requestedFreePoint,
			long requestedPaidPoint
	) {
		return new BusinessException(
				ErrorCode.COMMON_DATA_CONFLICT,
				"취소하려는 포인트 내역이 최초 결제 내역과 일치하지 않습니다.",
				Map.of(
						"recordedFreePoint", recordedUsage.freePoint(),
						"recordedPaidPoint", recordedUsage.paidPoint(),
						"requestedFreePoint", requestedFreePoint,
						"requestedPaidPoint", requestedPaidPoint
				)
		);
	}

	private record PurchasePointUsage(long freePoint, long paidPoint) {
	}
}
