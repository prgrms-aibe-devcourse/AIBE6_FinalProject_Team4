package com.kiwobollae.api.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.global.security.JwtTokenProvider;
import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import com.kiwobollae.api.payment.entity.ChargeProduct;
import com.kiwobollae.api.payment.repository.ChargeProductRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_admin_charge_product_test"
				+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AdminPaymentControllerAuthorizationTest {

	private static final long WAIT_SECONDS = 10L;
	private static final String ADMIN_PRODUCTS_PATH = "/api/v1/admin/payments/products";
	private static final String USER_PRODUCTS_PATH = "/api/v1/payments/products";

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Autowired private ChargeProductRepository chargeProductRepository;
	@Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
	private Long adminUserId;
	private Long regularUserId;

	@BeforeEach
	void clearChargeProducts() {
		idempotencyKeyRepository.deleteAll();
		chargeProductRepository.deleteAll();
		userRepository.deleteAll();
		adminUserId = saveUser("admin-charge@example.test", "admin-charge", UserRole.ADMIN).getId();
		regularUserId = saveUser("user-charge@example.test", "user-charge", UserRole.USER).getId();
	}

	@Test
	void anonymousUserCannotListChargeProductsForAdmin() throws Exception {
		mockMvc.perform(get(ADMIN_PRODUCTS_PATH))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));
	}

	@Test
	void chargeProductVersionHasLegacyWriterCompatibleDefault() {
		String defaultValue = jdbcTemplate.queryForObject(
				"""
						SELECT COLUMN_DEFAULT
						FROM information_schema.columns
						WHERE table_schema = DATABASE()
						  AND table_name = 'charge_products'
						  AND column_name = 'version'
						""",
				String.class
		);

		assertThat(defaultValue).isEqualTo("0");
	}

	@Test
	void regularUserCannotListChargeProductsForAdmin() throws Exception {
		String token = jwtTokenProvider.generateAccessToken(regularUserId, "USER");

		mockMvc.perform(get(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
	}

	@Test
	void adminListsActiveAndInactiveProductsByPriceThenId() throws Exception {
		ChargeProduct expensive = saveChargeProduct("고가 충전", 5_000L, 5_000L, true);
		ChargeProduct inactiveCheap = saveChargeProduct("비활성 저가 충전", 1_000L, 1_000L, false);
		ChargeProduct activeCheap = saveChargeProduct("활성 저가 충전", 1_000L, 1_100L, true);
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");

		mockMvc.perform(get(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(3))
				.andExpect(jsonPath("$.data[0].id").value(inactiveCheap.getId().intValue()))
				.andExpect(jsonPath("$.data[0].isActive").value(false))
				.andExpect(jsonPath("$.data[1].id").value(activeCheap.getId().intValue()))
				.andExpect(jsonPath("$.data[1].isActive").value(true))
				.andExpect(jsonPath("$.data[2].id").value(expensive.getId().intValue()))
				.andExpect(jsonPath("$.data[2].price").value(5_000));
	}

	@Test
	void createUpdateAndDeactivateAreReflectedInUserActiveProducts() throws Exception {
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");
		String userToken = jwtTokenProvider.generateAccessToken(regularUserId, "USER");

		mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken)
						.header("Idempotency-Key", "admin-product-create-flow")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"  봄 이벤트 충전  ","price":2000,"pointAmount":2200,"isActive":true}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value("봄 이벤트 충전"))
				.andExpect(jsonPath("$.data.isActive").value(true))
				.andExpect(jsonPath("$.data.version").value(0));

		ChargeProduct created = chargeProductRepository.findAll().getFirst();
		Long initialVersion = created.getVersion();

		mockMvc.perform(get(USER_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].id").value(created.getId().intValue()))
				.andExpect(jsonPath("$.data[0].price").value(2_000))
				.andExpect(jsonPath("$.data[0].pointAmount").value(2_200));

		mockMvc.perform(patch(ADMIN_PRODUCTS_PATH + "/" + created.getId())
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody(
								"  봄 이벤트 충전 수정  ",
								3_000L,
								3_500L,
								true,
								initialVersion
						)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("봄 이벤트 충전 수정"))
				.andExpect(jsonPath("$.data.price").value(3_000))
				.andExpect(jsonPath("$.data.pointAmount").value(3_500))
				.andExpect(jsonPath("$.data.version").value(initialVersion + 1));

		mockMvc.perform(get(USER_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].name").value("봄 이벤트 충전 수정"))
				.andExpect(jsonPath("$.data[0].price").value(3_000))
				.andExpect(jsonPath("$.data[0].pointAmount").value(3_500));

		mockMvc.perform(delete(ADMIN_PRODUCTS_PATH + "/" + created.getId())
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(USER_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));

		mockMvc.perform(get(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].id").value(created.getId().intValue()))
				.andExpect(jsonPath("$.data[0].isActive").value(false));
	}

	@Test
	void createRequiresIdempotencyKey() throws Exception {
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");

		mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"멱등키 없는 상품","price":2000,"pointAmount":2200,"isActive":true}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void createAndUpdateRejectPointAmountOutsideAllowedRate() throws Exception {
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");

		mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken)
						.header("Idempotency-Key", "invalid-point-rate-create")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"과다 지급 상품","price":1000,"pointAmount":1501,"isActive":true}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_CHARGE_PRODUCT_POINT_RATE_INVALID"));

		ChargeProduct product = saveChargeProduct("정상 상품", 1_000L, 1_100L, true);
		mockMvc.perform(patch(ADMIN_PRODUCTS_PATH + "/" + product.getId())
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody("저지급 상품", 1_000L, 999L, true, product.getVersion())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_CHARGE_PRODUCT_POINT_RATE_INVALID"));

		ChargeProduct unchanged = chargeProductRepository.findById(product.getId()).orElseThrow();
		assertThat(chargeProductRepository.count()).isEqualTo(1L);
		assertThat(unchanged.getName()).isEqualTo("정상 상품");
		assertThat(unchanged.getPrice()).isEqualTo(1_000L);
		assertThat(unchanged.getPointAmount()).isEqualTo(1_100L);
	}

	@Test
	void createDoesNotSuppressForeignKeyViolationForMissingAdmin() throws Exception {
		String staleAdminToken = jwtTokenProvider.generateAccessToken(Long.MAX_VALUE, "ADMIN");

		mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + staleAdminToken)
						.header("Idempotency-Key", "missing-admin-create")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"유효하지 않은 관리자","price":2000,"pointAmount":2200,"isActive":true}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COMMON_DATA_CONFLICT"));
	}

	@Test
	void sameCreateRequestAndIdempotencyKeyReplaysOriginalProduct() throws Exception {
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");
		String body = """
				{"name":"멱등 생성 상품","price":2000,"pointAmount":2200,"isActive":true}
				""";

		String firstResponse = mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken)
						.header("Idempotency-Key", "admin-product-create-replay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		String replayResponse = mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken)
						.header("Idempotency-Key", "admin-product-create-replay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(replayResponse).isEqualTo(firstResponse);
		assertThat(chargeProductRepository.count()).isEqualTo(1L);
	}

	@Test
	void sameCreateIdempotencyKeyWithDifferentPayloadIsRejected() throws Exception {
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");

		mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken)
						.header("Idempotency-Key", "admin-product-create-conflict")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"최초 상품","price":2000,"pointAmount":2200,"isActive":true}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken)
						.header("Idempotency-Key", "admin-product-create-conflict")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"다른 상품","price":3000,"pointAmount":3300,"isActive":true}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COMMON_IDEMPOTENCY_CONFLICT"));

		assertThat(chargeProductRepository.count()).isEqualTo(1L);
	}

	@Test
	void concurrentFirstCreateRequestsWithSameKeyReturnOneProduct() throws Exception {
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");
		String body = """
				{"name":"동시 멱등 상품","price":4000,"pointAmount":4400,"isActive":true}
				""";
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<MvcResult> attempt = () -> {
				ready.countDown();
				if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Concurrent create timed out");
				}
				return mockMvc.perform(post(ADMIN_PRODUCTS_PATH)
								.header("Authorization", "Bearer " + adminToken)
								.header("Idempotency-Key", "admin-product-create-concurrent")
								.contentType(MediaType.APPLICATION_JSON)
								.content(body))
						.andReturn();
			};
			Future<MvcResult> first = executor.submit(attempt);
			Future<MvcResult> second = executor.submit(attempt);
			assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			MvcResult firstResult = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
			MvcResult secondResult = second.get(WAIT_SECONDS, TimeUnit.SECONDS);
			assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
			assertThat(secondResult.getResponse().getStatus()).isEqualTo(201);
			assertThat(secondResult.getResponse().getContentAsString())
					.isEqualTo(firstResult.getResponse().getContentAsString());
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(chargeProductRepository.count()).isEqualTo(1L);
	}

	@Test
	void updateRequiresVersion() throws Exception {
		ChargeProduct product = saveChargeProduct("버전 필수 상품", 1_000L, 1_000L, true);
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");

		mockMvc.perform(patch(ADMIN_PRODUCTS_PATH + "/" + product.getId())
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"버전 없는 수정","price":2000,"pointAmount":2000,"isActive":true}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
	}

	@Test
	void staleVersionUpdateReturnsOptimisticLockConflict() throws Exception {
		ChargeProduct product = saveChargeProduct("최초 상품", 1_000L, 1_000L, true);
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");
		Long version = product.getVersion();

		mockMvc.perform(patch(ADMIN_PRODUCTS_PATH + "/" + product.getId())
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody("먼저 저장한 상품", 2_000L, 2_200L, true, version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(version + 1));

		mockMvc.perform(patch(ADMIN_PRODUCTS_PATH + "/" + product.getId())
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody("뒤늦은 수정", 3_000L, 3_300L, false, version)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COMMON_OPTIMISTIC_LOCK_CONFLICT"));

		mockMvc.perform(get(ADMIN_PRODUCTS_PATH)
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].name").value("먼저 저장한 상품"))
				.andExpect(jsonPath("$.data[0].price").value(2_000))
				.andExpect(jsonPath("$.data[0].isActive").value(true))
				.andExpect(jsonPath("$.data[0].version").value(version + 1));
	}

	@Test
	void concurrentUpdatesWithSameVersionAllowOnlyOneSuccess() throws Exception {
		ChargeProduct product = saveChargeProduct("동시 수정 상품", 1_000L, 1_000L, true);
		String adminToken = jwtTokenProvider.generateAccessToken(adminUserId, "ADMIN");
		Long version = product.getVersion();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<MvcResult> first = executor.submit(updateAttempt(
					product.getId(), "동시 수정 A", version, adminToken, ready, start));
			Future<MvcResult> second = executor.submit(updateAttempt(
					product.getId(), "동시 수정 B", version, adminToken, ready, start));
			assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(
					first.get(WAIT_SECONDS, TimeUnit.SECONDS).getResponse().getStatus(),
					second.get(WAIT_SECONDS, TimeUnit.SECONDS).getResponse().getStatus()
			)).containsExactlyInAnyOrder(200, 409);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		ChargeProduct updated = chargeProductRepository.findById(product.getId()).orElseThrow();
		assertThat(updated.getName()).isIn("동시 수정 A", "동시 수정 B");
		assertThat(updated.getVersion()).isEqualTo(version + 1);
	}

	private Callable<MvcResult> updateAttempt(
			Long productId,
			String name,
			Long version,
			String adminToken,
			CountDownLatch ready,
			CountDownLatch start
	) {
		return () -> {
			ready.countDown();
			if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent charge product update timed out");
			}
			return mockMvc.perform(patch(ADMIN_PRODUCTS_PATH + "/" + productId)
							.header("Authorization", "Bearer " + adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(updateBody(name, 2_000L, 2_200L, true, version)))
					.andReturn();
		};
	}

	private String updateBody(
			String name,
			Long price,
			Long pointAmount,
			Boolean isActive,
			Long version
	) {
		return """
				{"name":"%s","price":%d,"pointAmount":%d,"isActive":%s,"version":%d}
				""".formatted(name, price, pointAmount, isActive, version);
	}

	private ChargeProduct saveChargeProduct(
			String name,
			Long price,
			Long pointAmount,
			Boolean isActive
	) {
		return chargeProductRepository.saveAndFlush(ChargeProduct.builder()
				.name(name)
				.price(price)
				.pointAmount(pointAmount)
				.isActive(isActive)
				.build());
	}

	private User saveUser(String email, String nickname, UserRole role) {
		return userRepository.saveAndFlush(User.builder()
				.email(email)
				.password("encoded-password")
				.nickname(nickname)
				.name(nickname)
				.provider(AuthProvider.LOCAL)
				.role(role)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build());
	}
}
