package com.kiwobollae.api.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

	private Wallet wallet(long paid, long free) {
		return Wallet.builder().user(null).paidPoint(paid).freePoint(free).build();
	}

	@Test
	@DisplayName("유상 적립: paid_point 증가, balance_after = 새 유상 잔액")
	void applyDelta_paidCredit() {
		Wallet w = wallet(100L, 0L);
		when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(w));
		when(pointTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		PointTransaction tx = walletService.applyDelta(
				1L, PointTxType.CHARGE, CurrencyType.PAID, 500L, PointRefType.PAYMENT, 10L);

		assertThat(w.getPaidPoint()).isEqualTo(600L);
		assertThat(tx.getBalanceAfter()).isEqualTo(600L);
		assertThat(tx.getCurrencyType()).isEqualTo(CurrencyType.PAID);
		assertThat(tx.getAmount()).isEqualTo(500L);
	}

	@Test
	@DisplayName("유상 차감이 잔액 초과: POINT_INSUFFICIENT_BALANCE")
	void applyDelta_paidBelowZero_throws() {
		Wallet w = wallet(100L, 0L);
		when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(w));

		assertThatThrownBy(() -> walletService.applyDelta(
				1L, PointTxType.PURCHASE, CurrencyType.PAID, -200L, PointRefType.ORDER, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE);
	}

	@Test
	@DisplayName("무상 회수(CLAWBACK)는 음수 허용: free_point 음수 저장")
	void applyDelta_freeBelowZero_allowed() {
		Wallet w = wallet(0L, 50L);
		when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(w));
		when(pointTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		PointTransaction tx = walletService.applyDelta(
				1L, PointTxType.CLAWBACK, CurrencyType.FREE, -100L, PointRefType.JOURNAL_REVOCATION, 7L);

		assertThat(w.getFreePoint()).isEqualTo(-50L);
		assertThat(tx.getBalanceAfter()).isEqualTo(-50L);
	}

	@Test
	@DisplayName("지갑 없으면 POINT_WALLET_NOT_FOUND")
	void applyDelta_walletNotFound_throws() {
		when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> walletService.applyDelta(
				99L, PointTxType.CHARGE, CurrencyType.PAID, 100L, PointRefType.PAYMENT, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.POINT_WALLET_NOT_FOUND);
	}

	@Test
	@DisplayName("잔액 조회: paid+free 합산만 반환")
	void getWallet_returnsSum() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(1L);
		Wallet w = Wallet.builder().user(user).paidPoint(300L).freePoint(200L).build();
		when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(w));

		WalletResponse res = walletService.getWallet(1L);

		assertThat(res.userId()).isEqualTo(1L);
		assertThat(res.balance()).isEqualTo(500L);
	}
}
