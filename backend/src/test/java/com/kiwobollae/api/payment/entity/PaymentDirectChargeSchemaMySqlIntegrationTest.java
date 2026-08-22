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
	void directChargeSchemaHasNoChargeProductTableOrColumns() {
		assertThat(tableExists("charge_products")).isFalse();
		assertThat(columnExists("charge_product_id")).isFalse();
		assertThat(columnExists("charge_product_name")).isFalse();
		assertThat(directChargeCheckClause()).doesNotContain("charge_product");
	}

	@Test
	void chargeProductCleanupMigrationCanRunOnCurrentSchema() {
		runCleanupMigration();

		assertThat(tableExists("charge_products")).isFalse();
		assertThat(columnExists("charge_product_id")).isFalse();
		assertThat(columnExists("charge_product_name")).isFalse();
		assertThat(directChargeCheckClause()).doesNotContain("charge_product");
	}

	@Test
	void chargeProductCleanupMigrationRemovesLegacyForeignKeyColumnsAndTable() {
		runCleanupMigration();
		jdbcTemplate.execute("CREATE TABLE charge_products (id bigint NOT NULL PRIMARY KEY)");
		jdbcTemplate.execute("""
				ALTER TABLE payments
				    ADD COLUMN charge_product_id bigint NULL,
				    ADD COLUMN charge_product_name varchar(50) NULL,
				    ADD CONSTRAINT fk_payments_charge_product_cleanup_test
				        FOREIGN KEY (charge_product_id) REFERENCES charge_products(id)
				""");

		runCleanupMigration();

		assertThat(tableExists("charge_products")).isFalse();
		assertThat(columnExists("charge_product_id")).isFalse();
		assertThat(columnExists("charge_product_name")).isFalse();
		assertThat(directChargeCheckClause()).doesNotContain("charge_product");
	}

	@Test
	void directChargeConstraintAcceptsPolicyAmountAndRejectsInvalidAmount() {
		User user = saveUser();
		Payment saved = paymentRepository.saveAndFlush(payment(user, 12_340L, 12_340L));

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
				.cashAmount(cashAmount)
				.pointAmount(pointAmount)
				.status(PaymentStatus.PENDING)
				.provider(PaymentProviderType.TOSS)
				.providerOrderId("direct-charge-" + cashAmount)
				.build();
	}

	private boolean tableExists(String tableName) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) > 0
						FROM information_schema.tables
						WHERE table_schema = DATABASE()
						  AND table_name = ?
						""",
				Boolean.class,
				tableName
		);
	}

	private boolean columnExists(String columnName) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) > 0
						FROM information_schema.columns
						WHERE table_schema = DATABASE()
						  AND table_name = 'payments'
						  AND column_name = ?
						""",
				Boolean.class,
				columnName
		);
	}

	private String directChargeCheckClause() {
		return jdbcTemplate.queryForObject(
				"""
						SELECT LOWER(check_clause)
						FROM information_schema.check_constraints
						WHERE constraint_schema = DATABASE()
						  AND constraint_name = 'ck_payments_direct_charge_amount'
						""",
				String.class
		);
	}

	private void runCleanupMigration() {
		new ResourceDatabasePopulator(
				new ClassPathResource("db/reference/migrations/20260822-remove-charge-products.sql")
		).execute(dataSource);
	}
}
