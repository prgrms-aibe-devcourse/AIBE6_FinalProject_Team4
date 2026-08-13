package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentRequest;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.entity.ChargeProduct;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.repository.ChargeProductRepository;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.service.PointCreditService;
import jakarta.persistence.Column;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentChargeProductSnapshotTest {

	@Mock private ChargeProductRepository chargeProductRepository;
	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentRefundRepository paymentRefundRepository;
	@Mock private UserRepository userRepository;
	@Mock private PaymentProvider paymentProvider;
	@Mock private PointCreditService pointCreditService;
	@Mock private IdempotencyService idempotencyService;
	@Mock private PaymentStateService paymentStateService;
	@Mock private ObjectMapper objectMapper;

	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		paymentService = new PaymentService(
				chargeProductRepository,
				paymentRepository,
				paymentRefundRepository,
				userRepository,
				paymentProvider,
				pointCreditService,
				idempotencyService,
				paymentStateService,
				objectMapper
		);
	}

	@Test
	void requestChargeSnapshotsProductNameAndKeepsItAfterProductUpdate() throws Exception {
		User user = org.mockito.Mockito.mock(User.class);
		ChargeProduct chargeProduct = chargeProduct("첫 결제 상품");
		IdempotencyKey idempotencyKey = org.mockito.Mockito.mock(IdempotencyKey.class);
		ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);

		given(user.getId()).willReturn(7L);
		given(userRepository.getReferenceById(7L)).willReturn(user);
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("PAYMENT_CHARGE"),
				org.mockito.ArgumentMatchers.eq("charge-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(idempotencyKey, false));
		given(chargeProductRepository.findById(3L)).willReturn(Optional.of(chargeProduct));
		given(paymentProvider.getType()).willReturn(PaymentProviderType.TOSS);
		given(paymentRepository.save(paymentCaptor.capture()))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any(PaymentResponse.class)))
				.willReturn("{}");

		PaymentResponse created = paymentService.requestCharge(
				7L,
				"charge-key",
				new PaymentRequest(3L)
		);
		Payment savedPayment = paymentCaptor.getValue();

		chargeProduct.update("관리자가 바꾼 이름", 2_000L, 2_200L, true);
		PaymentResponse history = PaymentResponse.from(savedPayment);

		assertThat(created.chargeProductName()).isEqualTo("첫 결제 상품");
		assertThat(history.chargeProductName()).isEqualTo("첫 결제 상품");
	}

	@Test
	void legacyPaymentWithoutSnapshotFallsBackToCurrentProductName() {
		Payment legacyPayment = legacyPayment(null);

		assertThat(PaymentResponse.from(legacyPayment).chargeProductName())
				.isEqualTo("구 데이터 상품명");
	}

	@Test
	void legacyBlankSnapshotFallsBackToCurrentProductName() {
		Payment legacyPayment = legacyPayment("   ");

		assertThat(PaymentResponse.from(legacyPayment).chargeProductName())
				.isEqualTo("구 데이터 상품명");
	}

	@Test
	void snapshotColumnRemainsNullableDuringExpandDeployment() throws Exception {
		Column column = Payment.class.getDeclaredField("chargeProductName").getAnnotation(Column.class);

		assertThat(column).isNotNull();
		assertThat(column.nullable()).isTrue();
	}

	private Payment legacyPayment(String chargeProductName) {
		User user = org.mockito.Mockito.mock(User.class);
		given(user.getId()).willReturn(7L);
		return Payment.builder()
				.user(user)
				.chargeProduct(chargeProduct("구 데이터 상품명"))
				.chargeProductName(chargeProductName)
				.cashAmount(1_000L)
				.pointAmount(1_000L)
				.status(PaymentStatus.COMPLETED)
				.provider(PaymentProviderType.TOSS)
				.providerOrderId("legacy-order")
				.build();
	}

	private ChargeProduct chargeProduct(String name) {
		return ChargeProduct.builder()
				.name(name)
				.price(1_000L)
				.pointAmount(1_000L)
				.isActive(true)
				.build();
	}
}
