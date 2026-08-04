package com.kiwobollae.api.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.JournalRewardResult;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private PointTransactionRepository pointTransactionRepository;

	@InjectMocks
	private WalletService walletService;

	@Test
	void walletResponseContainsTotalPaidAndFreePoints() {
		User user = mock(User.class);
		given(user.getId()).willReturn(7L);
		Wallet wallet = Wallet.builder()
				.user(user)
				.paidPoint(500L)
				.freePoint(300L)
				.build();
		given(walletRepository.findByUserId(7L)).willReturn(Optional.of(wallet));

		WalletResponse response = walletService.getWallet(7L);

		assertThat(response.userId()).isEqualTo(7L);
		assertThat(response.balance()).isEqualTo(800L);
		assertThat(response.paidPoint()).isEqualTo(500L);
		assertThat(response.freePoint()).isEqualTo(300L);
	}

	@Test
	void journalRewardIncreasesFreePointAndWritesJournalLedger() {
		Wallet wallet = Wallet.builder().freePoint(200L).paidPoint(500L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		JournalRewardResult result = walletService.rewardJournal(7L, 31L);

		assertThat(result.rewardAmount()).isEqualTo(100L);
		assertThat(wallet.getFreePoint()).isEqualTo(300L);
		assertThat(wallet.getPaidPoint()).isEqualTo(500L);
		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(PointTxType.JOURNAL_REWARD);
		assertThat(captor.getValue().getCurrencyType()).isEqualTo(CurrencyType.FREE);
		assertThat(captor.getValue().getAmount()).isEqualTo(100L);
		assertThat(captor.getValue().getBalanceAfter()).isEqualTo(300L);
		assertThat(captor.getValue().getRefType()).isEqualTo(PointRefType.JOURNAL_COMPLETION);
		assertThat(captor.getValue().getRefId()).isEqualTo(31L);
	}

	@Test
	void journalRewardRejectsDuplicateJournalId() {
		Wallet wallet = Wallet.builder().freePoint(200L).paidPoint(500L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				PointTxType.JOURNAL_REWARD,
				PointRefType.JOURNAL_COMPLETION,
				31L
		)).willReturn(true);

		assertThatThrownBy(() -> walletService.rewardJournal(7L, 31L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.POINT_DUPLICATE_TRANSACTION));

		assertThat(wallet.getFreePoint()).isEqualTo(200L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void cardPurchaseUsesFreePointFirstWhenFreeBalanceCoversTotal() {
		Wallet wallet = Wallet.builder().freePoint(700L).paidPoint(500L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		PointDeductionResult result = walletService.deductForPurchase(
				7L,
				600L,
				PointRefType.CARD_PURCHASE,
				11L
		);

		assertThat(result.usedFreePoint()).isEqualTo(600L);
		assertThat(result.usedPaidPoint()).isZero();
		assertThat(result.remainingBalance()).isEqualTo(600L);
		assertThat(wallet.getFreePoint()).isEqualTo(100L);
		assertThat(wallet.getPaidPoint()).isEqualTo(500L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository).save(captor.capture());
		assertThat(captor.getValue().getCurrencyType()).isEqualTo(CurrencyType.FREE);
		assertThat(captor.getValue().getAmount()).isEqualTo(-600L);
		assertThat(captor.getValue().getBalanceAfter()).isEqualTo(100L);
	}

	@Test
	void cardPurchaseUsesPaidPointForFreePointShortage() {
		Wallet wallet = Wallet.builder().freePoint(100L).paidPoint(1_000L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		PointDeductionResult result = walletService.deductForPurchase(
				7L,
				200L,
				PointRefType.CARD_PURCHASE,
				11L
		);

		assertThat(result.usedFreePoint()).isEqualTo(100L);
		assertThat(result.usedPaidPoint()).isEqualTo(100L);
		assertThat(result.remainingBalance()).isEqualTo(900L);
		assertThat(wallet.getFreePoint()).isZero();
		assertThat(wallet.getPaidPoint()).isEqualTo(900L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getCurrencyType)
				.containsExactly(CurrencyType.FREE, CurrencyType.PAID);
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getAmount)
				.containsExactly(-100L, -100L);
	}

	@Test
	void gachaPackPurchaseUsesFreePointFirstAndRecordsGachaReference() {
		Wallet wallet = Wallet.builder().freePoint(100L).paidPoint(1_000L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		PointDeductionResult result = walletService.deductForGachaPurchase(7L, 200L, 501L);

		assertThat(result.usedFreePoint()).isEqualTo(100L);
		assertThat(result.usedPaidPoint()).isEqualTo(100L);
		assertThat(result.remainingBalance()).isEqualTo(900L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getRefType)
				.containsOnly(PointRefType.GACHA_PURCHASE);
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getRefId)
				.containsOnly(501L);
	}

	@Test
	void cardPurchaseUsesOnlyPaidPointWhenFreePointIsNegative() {
		Wallet wallet = Wallet.builder().freePoint(-100L).paidPoint(200L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		PointDeductionResult result = walletService.deductForPurchase(
				7L,
				150L,
				PointRefType.CARD_PURCHASE,
				11L
		);

		assertThat(result.usedFreePoint()).isZero();
		assertThat(result.usedPaidPoint()).isEqualTo(150L);
		assertThat(wallet.getFreePoint()).isEqualTo(-100L);
		assertThat(wallet.getPaidPoint()).isEqualTo(50L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository).save(captor.capture());
		assertThat(captor.getValue().getCurrencyType()).isEqualTo(CurrencyType.PAID);
		assertThat(captor.getValue().getAmount()).isEqualTo(-150L);
		assertThat(captor.getValue().getBalanceAfter()).isEqualTo(50L);
	}

	@Test
	void cardPurchaseRejectsInsufficientCombinedPointWithoutPartialDeduction() {
		Wallet wallet = Wallet.builder().freePoint(100L).paidPoint(99L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		assertThatThrownBy(() -> walletService.deductForPurchase(
				7L,
				200L,
				PointRefType.CARD_PURCHASE,
				11L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));

		assertThat(wallet.getFreePoint()).isEqualTo(100L);
		assertThat(wallet.getPaidPoint()).isEqualTo(99L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void orderPurchaseUsesOnlyPaidPointByDefault() {
		Wallet wallet = Wallet.builder().freePoint(500L).paidPoint(700L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		PointDeductionResult result = walletService.deductForOrderPurchase(
				7L,
				600L,
				0L,
				21L
		);

		assertThat(result.usedFreePoint()).isZero();
		assertThat(result.usedPaidPoint()).isEqualTo(600L);
		assertThat(result.remainingBalance()).isEqualTo(600L);
		assertThat(wallet.getFreePoint()).isEqualTo(500L);
		assertThat(wallet.getPaidPoint()).isEqualTo(100L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository).save(captor.capture());
		assertThat(captor.getValue().getCurrencyType()).isEqualTo(CurrencyType.PAID);
		assertThat(captor.getValue().getAmount()).isEqualTo(-600L);
		assertThat(captor.getValue().getRefType()).isEqualTo(PointRefType.ORDER);
	}

	@Test
	void orderPurchaseUsesRequestedFreePointAndPaidPointTogether() {
		Wallet wallet = Wallet.builder().freePoint(500L).paidPoint(700L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		PointDeductionResult result = walletService.deductForOrderPurchase(
				7L,
				650L,
				200L,
				21L
		);

		assertThat(result.usedFreePoint()).isEqualTo(200L);
		assertThat(result.usedPaidPoint()).isEqualTo(450L);
		assertThat(result.remainingBalance()).isEqualTo(550L);
		assertThat(wallet.getFreePoint()).isEqualTo(300L);
		assertThat(wallet.getPaidPoint()).isEqualTo(250L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getCurrencyType)
				.containsExactly(CurrencyType.FREE, CurrencyType.PAID);
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getAmount)
				.containsExactly(-200L, -450L);
	}

	@Test
	void orderPurchaseRejectsFreePointThatIsNotInHundredPointUnits() {
		assertThatThrownBy(() -> walletService.deductForOrderPurchase(
				7L,
				650L,
				250L,
				21L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		verify(walletRepository, never()).findByUserIdForUpdate(org.mockito.ArgumentMatchers.anyLong());
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void orderPurchaseRejectsRequestedFreePointAboveAvailableFreePoint() {
		Wallet wallet = Wallet.builder().freePoint(100L).paidPoint(1_000L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		assertThatThrownBy(() -> walletService.deductForOrderPurchase(
				7L,
				500L,
				200L,
				21L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));

		assertThat(wallet.getFreePoint()).isEqualTo(100L);
		assertThat(wallet.getPaidPoint()).isEqualTo(1_000L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void orderPurchaseRejectsInsufficientPaidPointForRemainder() {
		Wallet wallet = Wallet.builder().freePoint(1_000L).paidPoint(299L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		assertThatThrownBy(() -> walletService.deductForOrderPurchase(
				7L,
				500L,
				200L,
				21L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));

		assertThat(wallet.getFreePoint()).isEqualTo(1_000L);
		assertThat(wallet.getPaidPoint()).isEqualTo(299L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void restorePurchasePointsRestoresEachCurrencyAndWritesLedgers() {
		Wallet wallet = Wallet.builder().freePoint(0L).paidPoint(200L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.findAllByWalletAndTypeAndRefTypeAndRefId(
				wallet,
				PointTxType.PURCHASE,
				PointRefType.CARD_PURCHASE,
				11L
		)).willReturn(List.of(
				PointTransaction.builder()
						.currencyType(CurrencyType.FREE)
						.amount(-300L)
						.build(),
				PointTransaction.builder()
						.currencyType(CurrencyType.PAID)
						.amount(-300L)
						.build()
		));

		walletService.restorePurchasePoints(7L, 300L, 300L, PointRefType.CARD_PURCHASE, 11L);

		assertThat(wallet.getFreePoint()).isEqualTo(300L);
		assertThat(wallet.getPaidPoint()).isEqualTo(500L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getType)
				.containsExactly(PointTxType.RESTORE, PointTxType.RESTORE);
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getCurrencyType)
				.containsExactly(CurrencyType.FREE, CurrencyType.PAID);
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getAmount)
				.containsExactly(300L, 300L);
	}

	@Test
	void restorePurchasePointsRejectsUsageThatDoesNotMatchPurchaseLedger() {
		Wallet wallet = Wallet.builder().freePoint(0L).paidPoint(200L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.findAllByWalletAndTypeAndRefTypeAndRefId(
				wallet,
				PointTxType.PURCHASE,
				PointRefType.CARD_PURCHASE,
				11L
		)).willReturn(List.of(
				PointTransaction.builder()
						.currencyType(CurrencyType.FREE)
						.amount(-300L)
						.build(),
				PointTransaction.builder()
						.currencyType(CurrencyType.PAID)
						.amount(-300L)
						.build()
		));

		assertThatThrownBy(() -> walletService.restorePurchasePoints(
				7L,
				300L,
				299L,
				PointRefType.CARD_PURCHASE,
				11L
		)).isInstanceOfSatisfying(BusinessException.class, exception -> {
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_DATA_CONFLICT);
			assertThat(exception.getDetails()).containsEntry("recordedFreePoint", 300L);
			assertThat(exception.getDetails()).containsEntry("recordedPaidPoint", 300L);
			assertThat(exception.getDetails()).containsEntry("requestedFreePoint", 300L);
			assertThat(exception.getDetails()).containsEntry("requestedPaidPoint", 299L);
		});

		assertThat(wallet.getFreePoint()).isZero();
		assertThat(wallet.getPaidPoint()).isEqualTo(200L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void restorePurchasePointsRejectsMissingPurchaseLedger() {
		Wallet wallet = Wallet.builder().freePoint(0L).paidPoint(200L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.findAllByWalletAndTypeAndRefTypeAndRefId(
				wallet,
				PointTxType.PURCHASE,
				PointRefType.ORDER,
				21L
		)).willReturn(List.of());

		assertThatThrownBy(() -> walletService.restorePurchasePoints(
				7L,
				100L,
				200L,
				PointRefType.ORDER,
				21L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_DATA_CONFLICT));

		assertThat(wallet.getFreePoint()).isZero();
		assertThat(wallet.getPaidPoint()).isEqualTo(200L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void restorePurchasePointsRejectsDuplicateRestore() {
		Wallet wallet = Wallet.builder().freePoint(0L).paidPoint(200L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				PointTxType.RESTORE,
				PointRefType.CARD_PURCHASE,
				11L
		)).willReturn(true);

		assertThatThrownBy(() -> walletService.restorePurchasePoints(
				7L,
				300L,
				300L,
				PointRefType.CARD_PURCHASE,
				11L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_DUPLICATE_TRANSACTION));

		assertThat(wallet.getFreePoint()).isZero();
		assertThat(wallet.getPaidPoint()).isEqualTo(200L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void restorePurchasePointsRejectsZeroOrNegativeUsage() {
		assertThatThrownBy(() -> walletService.restorePurchasePoints(
				7L,
				0L,
				0L,
				PointRefType.CARD_PURCHASE,
				11L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		assertThatThrownBy(() -> walletService.restorePurchasePoints(
				7L,
				-1L,
				1L,
				PointRefType.CARD_PURCHASE,
				11L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		verify(walletRepository, never()).findByUserIdForUpdate(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void restoresGachaPurchaseUsingRecordedFreeAndPaidPointAmounts() {
		Wallet wallet = Wallet.builder().freePoint(0L).paidPoint(0L).build();
		PointTransaction freePurchase = mock(PointTransaction.class);
		PointTransaction paidPurchase = mock(PointTransaction.class);
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.findAllByWalletAndTypeAndRefTypeAndRefId(
				wallet,
				PointTxType.PURCHASE,
				PointRefType.GACHA_PURCHASE,
				501L
		)).willReturn(List.of(freePurchase, paidPurchase));
		given(freePurchase.getAmount()).willReturn(-60L);
		given(freePurchase.getCurrencyType()).willReturn(CurrencyType.FREE);
		given(paidPurchase.getAmount()).willReturn(-40L);
		given(paidPurchase.getCurrencyType()).willReturn(CurrencyType.PAID);

		walletService.restoreGachaPurchasePoints(7L, 501L);

		assertThat(wallet.getFreePoint()).isEqualTo(60L);
		assertThat(wallet.getPaidPoint()).isEqualTo(40L);
		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getAmount)
				.containsExactly(60L, 40L);
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getRefType)
				.containsOnly(PointRefType.GACHA_PURCHASE);
	}

	@Test
	void duplicateGachaPurchaseRestoreIsIdempotent() {
		Wallet wallet = Wallet.builder().freePoint(0L).paidPoint(0L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				PointTxType.RESTORE,
				PointRefType.GACHA_PURCHASE,
				501L
		)).willReturn(true);

		walletService.restoreGachaPurchasePoints(7L, 501L);

		assertThat(wallet.getFreePoint()).isZero();
		assertThat(wallet.getPaidPoint()).isZero();
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void paymentRefundDeductsOnlyPaidPointAndWritesRefundLedger() {
		Wallet wallet = Wallet.builder().freePoint(900L).paidPoint(5_000L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		walletService.deductPaidPointForPaymentRefund(7L, 3_000L, 41L);

		assertThat(wallet.getPaidPoint()).isEqualTo(2_000L);
		assertThat(wallet.getFreePoint()).isEqualTo(900L);
		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(PointTxType.REFUND);
		assertThat(captor.getValue().getCurrencyType()).isEqualTo(CurrencyType.PAID);
		assertThat(captor.getValue().getAmount()).isEqualTo(-3_000L);
		assertThat(captor.getValue().getBalanceAfter()).isEqualTo(2_000L);
		assertThat(captor.getValue().getRefType()).isEqualTo(PointRefType.PAYMENT_REFUND);
		assertThat(captor.getValue().getRefId()).isEqualTo(41L);
	}

	@Test
	void paymentRefundRejectsInsufficientPaidPointWithoutUsingFreePoint() {
		Wallet wallet = Wallet.builder().freePoint(10_000L).paidPoint(2_999L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		assertThatThrownBy(() ->
				walletService.deductPaidPointForPaymentRefund(7L, 3_000L, 41L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));

		assertThat(wallet.getPaidPoint()).isEqualTo(2_999L);
		assertThat(wallet.getFreePoint()).isEqualTo(10_000L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void paymentRefundRejectsDuplicateRefundLedger() {
		Wallet wallet = Wallet.builder().freePoint(900L).paidPoint(5_000L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.existsByTypeAndRefTypeAndRefId(
				PointTxType.REFUND,
				PointRefType.PAYMENT_REFUND,
				41L
		)).willReturn(true);

		assertThatThrownBy(() ->
				walletService.deductPaidPointForPaymentRefund(7L, 3_000L, 41L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.POINT_DUPLICATE_TRANSACTION));

		assertThat(wallet.getPaidPoint()).isEqualTo(5_000L);
		assertThat(wallet.getFreePoint()).isEqualTo(900L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void adminAdjustmentGrantsPaidPointAndWritesAdminLedger() {
		User user = mock(User.class);
		Wallet wallet = Wallet.builder()
				.user(user)
				.freePoint(300L)
				.paidPoint(500L)
				.build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.save(org.mockito.ArgumentMatchers.any(PointTransaction.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		var response = walletService.adjustByAdmin(1L, 7L, CurrencyType.PAID, 200L);

		assertThat(response.userId()).isEqualTo(7L);
		assertThat(response.currencyType()).isEqualTo(CurrencyType.PAID);
		assertThat(response.amount()).isEqualTo(200L);
		assertThat(response.balanceAfter()).isEqualTo(700L);
		assertThat(response.paidPoint()).isEqualTo(700L);
		assertThat(response.freePoint()).isEqualTo(300L);
		assertThat(response.balance()).isEqualTo(1_000L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(PointTxType.ADMIN_ADJUST);
		assertThat(captor.getValue().getCurrencyType()).isEqualTo(CurrencyType.PAID);
		assertThat(captor.getValue().getAmount()).isEqualTo(200L);
		assertThat(captor.getValue().getRefType()).isEqualTo(PointRefType.ADMIN);
		assertThat(captor.getValue().getRefId()).isEqualTo(1L);
	}

	@Test
	void adminAdjustmentCanDeductFreePointDownToZero() {
		Wallet wallet = Wallet.builder().freePoint(300L).paidPoint(500L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));
		given(pointTransactionRepository.save(org.mockito.ArgumentMatchers.any(PointTransaction.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		var response = walletService.adjustByAdmin(1L, 7L, CurrencyType.FREE, -300L);

		assertThat(response.balanceAfter()).isZero();
		assertThat(response.freePoint()).isZero();
		assertThat(response.paidPoint()).isEqualTo(500L);
		assertThat(response.balance()).isEqualTo(500L);
	}

	@Test
	void adminAdjustmentRejectsPaidAndFreePointUnderflowWithoutMutation() {
		Wallet paidWallet = Wallet.builder().freePoint(300L).paidPoint(500L).build();
		Wallet freeWallet = Wallet.builder().freePoint(300L).paidPoint(500L).build();
		given(walletRepository.findByUserIdForUpdate(7L))
				.willReturn(Optional.of(paidWallet))
				.willReturn(Optional.of(freeWallet));

		assertThatThrownBy(() -> walletService.adjustByAdmin(1L, 7L, CurrencyType.PAID, -501L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));
		assertThatThrownBy(() -> walletService.adjustByAdmin(1L, 7L, CurrencyType.FREE, -301L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));

		assertThat(paidWallet.getPaidPoint()).isEqualTo(500L);
		assertThat(paidWallet.getFreePoint()).isEqualTo(300L);
		assertThat(freeWallet.getPaidPoint()).isEqualTo(500L);
		assertThat(freeWallet.getFreePoint()).isEqualTo(300L);
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void adminAdjustmentRejectsZeroAmountBeforeLockingWallet() {
		assertThatThrownBy(() -> walletService.adjustByAdmin(1L, 7L, CurrencyType.FREE, 0L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		verify(walletRepository, never()).findByUserIdForUpdate(org.mockito.ArgumentMatchers.anyLong());
		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
