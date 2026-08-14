package com.kiwobollae.api.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.repository.ChargeProductRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_payment_snapshot_schema_test"
				+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentSnapshotSchemaMySqlIntegrationTest {

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private DataSource dataSource;
	@Autowired private UserRepository userRepository;
	@Autowired private ChargeProductRepository chargeProductRepository;
	@Autowired private PaymentRepository paymentRepository;

	@Test
	void hibernateCreatesSnapshotColumnAsNullableDuringExpandDeployment() {
		assertThat(snapshotColumnNullable()).isEqualTo("YES");
	}

	@Test
	void referenceExpandMigrationCanRunAfterHibernateAlreadyAddedColumn() {
		User user = userRepository.saveAndFlush(User.builder()
				.email("snapshot-schema@example.test")
				.password("encoded-password")
				.nickname("snapshot-schema")
				.name("스냅샷 스키마")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build());
		ChargeProduct chargeProduct = chargeProductRepository.saveAndFlush(
				ChargeProduct.builder()
						.name("기존 충전 상품")
						.price(1_000L)
						.pointAmount(1_000L)
						.isActive(true)
						.build()
		);
		jdbcTemplate.update(
				"""
						INSERT INTO payments (
						    user_id, charge_product_id, cash_amount, point_amount,
						    status, provider, provider_order_id, created_at, updated_at
						) VALUES (?, ?, 1000, 1000, 'PENDING', 'TOSS', ?, NOW(6), NOW(6))
						""",
				user.getId(),
				chargeProduct.getId(),
				"legacy-null-snapshot"
		);
		jdbcTemplate.update(
				"""
						INSERT INTO payments (
						    user_id, charge_product_id, charge_product_name, cash_amount, point_amount,
						    status, provider, provider_order_id, created_at, updated_at
						) VALUES (?, ?, '  ', 1000, 1000, 'PENDING', 'TOSS', ?, NOW(6), NOW(6))
						""",
				user.getId(),
				chargeProduct.getId(),
				"legacy-blank-snapshot"
		);

		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
				new ClassPathResource(
						"db/reference/migrations/20260812-payment-charge-product-name-snapshot.sql")
		);

		populator.execute(dataSource);
		jdbcTemplate.update(
				"""
						INSERT INTO payments (
						    user_id, charge_product_id, cash_amount, point_amount,
						    status, provider, provider_order_id, created_at, updated_at
						) VALUES (?, ?, 1000, 1000, 'PENDING', 'TOSS', ?, NOW(6), NOW(6))
						""",
				user.getId(),
				chargeProduct.getId(),
				"old-instance-after-expand"
		);
		Long oldInstancePaymentId = jdbcTemplate.queryForObject(
				"SELECT id FROM payments WHERE provider_order_id = 'old-instance-after-expand'",
				Long.class
		);

		assertThat(snapshotColumnNullable()).isEqualTo("YES");
		assertThat(jdbcTemplate.queryForList(
				"""
						SELECT charge_product_name
						FROM payments
						WHERE provider_order_id IN ('legacy-null-snapshot', 'legacy-blank-snapshot')
						ORDER BY provider_order_id
						""",
				String.class
		)).containsExactly("기존 충전 상품", "기존 충전 상품");
		assertThat(PaymentResponse.from(
				paymentRepository.findDetailsById(oldInstancePaymentId).orElseThrow()
		).chargeProductName()).isEqualTo("기존 충전 상품");
	}

	private String snapshotColumnNullable() {
		return jdbcTemplate.queryForObject(
				"""
						SELECT IS_NULLABLE
						FROM information_schema.columns
						WHERE table_schema = DATABASE()
						  AND table_name = 'payments'
						  AND column_name = 'charge_product_name'
						""",
				String.class
		);
	}
}
