package com.kiwobollae.api.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_payment_direct_charge_schema_test"
				+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentDirectChargeSchemaMySqlIntegrationTest {

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private DataSource dataSource;
	@Autowired private UserRepository userRepository;
	@Autowired private PaymentRepository paymentRepository;

	@Test
	void directChargeColumnsAllowNoProductAndRequireSnapshotName() {
		assertThat(columnNullable("charge_product_id")).isEqualTo("YES");
		assertThat(columnNullable("charge_product_name")).isEqualTo("NO");
	}

	@Test
	void directChargeReferenceMigrationCanRunOnCurrentSchema() {
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
				new ClassPathResource("db/reference/migrations/20260821-payment-direct-charge.sql")
		);

		populator.execute(dataSource);

		assertThat(columnNullable("charge_product_id")).isEqualTo("YES");
		assertThat(columnNullable("charge_product_name")).isEqualTo("NO");
	}

	@Test
	void directChargeConstraintAcceptsPolicyAmountAndRejectsInvalidAmount() {
		User user = saveUser();
		Payment saved = paymentRepository.saveAndFlush(payment(user, 12_340L, 12_340L));

		assertThat(saved.getChargeProductId()).isNull();
		assertThat(saved.getCashAmount()).isEqualTo(saved.getPointAmount());
		assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment(user, 2_801L, 2_801L)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private User saveUser() {
		return userRepository.saveAndFlush(User.builder()
				.email("direct-charge-schema@example.test")
				.password("encoded-password")
				.nickname("direct-charge-schema")
				.name("직접 충전 스키마")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build());
	}

	private Payment payment(User user, Long cashAmount, Long pointAmount) {
		return Payment.builder()
				.user(user)
				.chargeProductId(null)
				.chargeProductName("직접 충전")
				.cashAmount(cashAmount)
				.pointAmount(pointAmount)
				.status(PaymentStatus.PENDING)
				.provider(PaymentProviderType.TOSS)
				.providerOrderId("direct-charge-" + cashAmount)
				.build();
	}

	private String columnNullable(String columnName) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT IS_NULLABLE
						FROM information_schema.columns
						WHERE table_schema = DATABASE()
						  AND table_name = 'payments'
						  AND column_name = ?
						""",
				String.class,
				columnName
		);
	}
}
