package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.infra.entity.enums.IdempotencyStatus;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import com.kiwobollae.api.payment.dto.request.PaymentConfirmRequest;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentConfirmResult;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ActiveProfiles("test")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_payment_confirmation_test"
						+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
				"spring.jpa.hibernate.ddl-auto=create-drop"
		}
)
class PaymentConfirmationTransactionMySqlIntegrationTest {

	@Autowired private PaymentService paymentService;
	@Autowired private PaymentRepository paymentRepository;
	@Autowired private WalletRepository walletRepository;
	@Autowired private PointTransactionRepository pointTransactionRepository;
	@Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
	@Autowired private UserRepository userRepository;

	@MockitoBean private PaymentProvider paymentProvider;

	private Long userId;

	@BeforeEach
	void setUp() {
		clearData();
		given(paymentProvider.getType()).willReturn(PaymentProviderType.TOSS);
		given(paymentProvider.confirm(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return PaymentConfirmResult.success();
		});

		User user = userRepository.saveAndFlush(User.builder()
				.email("payment-confirmation-integration@example.test")
				.password("encoded-password")
				.nickname("payment-confirmation-integration")
				.name("결제승인통합테스트")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build());
		userId = user.getId();

		paymentRepository.saveAndFlush(Payment.builder()
				.user(user)
				.cashAmount(5_000L)
				.pointAmount(5_000L)
				.status(PaymentStatus.PENDING)
				.provider(PaymentProviderType.TOSS)
				.providerOrderId("confirmation-integration-order")
				.build());

		walletRepository.saveAndFlush(Wallet.builder()
				.user(user)
				.paidPoint(0L)
				.freePoint(0L)
				.build());
	}

	@AfterEach
	void tearDown() {
		clearData();
	}

	@Test
	void confirmsOutsideTransactionThenCreditsPointInFinalTransaction() {
		var response = paymentService.confirmPayment(
				userId,
				"confirmation-integration-key",
				new PaymentConfirmRequest(
						"confirmation-integration-order",
						"confirmation-integration-payment-key",
						5_000L
				)
		);

		assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
		assertThat(response.providerPaymentKey()).isEqualTo("confirmation-integration-payment-key");
		assertThat(walletRepository.findByUserId(userId).orElseThrow().getPaidPoint()).isEqualTo(5_000L);
		assertThat(pointTransactionRepository.findAll())
				.singleElement()
				.satisfies(transaction -> {
					assertThat(transaction.getType()).isEqualTo(PointTxType.CHARGE);
					assertThat(transaction.getCurrencyType()).isEqualTo(CurrencyType.PAID);
					assertThat(transaction.getAmount()).isEqualTo(5_000L);
					assertThat(transaction.getRefType()).isEqualTo(PointRefType.PAYMENT);
				});
		assertThat(idempotencyKeyRepository.findAll())
				.singleElement()
				.satisfies(key -> assertThat(key.getStatus()).isEqualTo(IdempotencyStatus.SUCCEEDED));
	}

	private void clearData() {
		idempotencyKeyRepository.deleteAllInBatch();
		pointTransactionRepository.deleteAllInBatch();
		paymentRepository.deleteAllInBatch();
		walletRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}
}
