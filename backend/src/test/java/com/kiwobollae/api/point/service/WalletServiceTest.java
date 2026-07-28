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
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
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
	void purchaseUsesFreePointBeforePaidPointAndWritesLedgers() {
		Wallet wallet = Wallet.builder().freePoint(300L).paidPoint(500L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		PointDeductionResult result = walletService.deductForPurchase(
				7L,
				600L,
				PointRefType.CARD_PURCHASE,
				11L
		);

		assertThat(result.usedFreePoint()).isEqualTo(300L);
		assertThat(result.usedPaidPoint()).isEqualTo(300L);
		assertThat(result.remainingBalance()).isEqualTo(200L);
		assertThat(wallet.getFreePoint()).isZero();
		assertThat(wallet.getPaidPoint()).isEqualTo(200L);

		ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
		verify(pointTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
				.extracting(PointTransaction::getAmount)
				.containsExactly(-300L, -300L);
	}

	@Test
	void purchaseRejectsInsufficientTotalBalance() {
		Wallet wallet = Wallet.builder().freePoint(100L).paidPoint(200L).build();
		given(walletRepository.findByUserIdForUpdate(7L)).willReturn(Optional.of(wallet));

		assertThatThrownBy(() -> walletService.deductForPurchase(
				7L,
				301L,
				PointRefType.CARD_PURCHASE,
				11L
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));

		verify(pointTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
