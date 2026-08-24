package com.kiwobollae.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.dto.response.AdminUserSummaryResponse;
import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.AdminUserQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_admin_user_query_test"
						+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
				"spring.jpa.hibernate.ddl-auto=create-drop"
		}
)
class AdminUserQueryServiceMySqlIntegrationTest {

	@Autowired private AdminUserQueryService adminUserQueryService;
	@Autowired private AdminUserQueryRepository adminUserQueryRepository;

	private Long greenUserId;

	@BeforeEach
	void setUp() {
		adminUserQueryRepository.deleteAllInBatch();
		greenUserId = saveUser("Green@example.com", "초록", "김초록", UserStatus.ACTIVE).getId();
		saveUser("basil@example.com", "바질", "박바질", UserStatus.ACTIVE);
		saveUser("suspended@example.com", "정지회원", "김정지", UserStatus.SUSPENDED);
	}

	@Test
	void searchesByEmailNicknameNameAndExactId() {
		assertThat(search("green", null).getContent())
				.extracting(AdminUserSummaryResponse::id)
				.containsExactly(greenUserId);
		assertThat(search("초록", null).getContent())
				.extracting(AdminUserSummaryResponse::id)
				.containsExactly(greenUserId);
		assertThat(search("김초록", null).getContent())
				.extracting(AdminUserSummaryResponse::id)
				.containsExactly(greenUserId);
		assertThat(search(String.valueOf(greenUserId), null).getContent())
				.extracting(AdminUserSummaryResponse::id)
				.containsExactly(greenUserId);
	}

	@Test
	void filtersStatusAndPaginatesWithMaximumPageSize() {
		Page<AdminUserSummaryResponse> activeUsers = adminUserQueryService.search(
				"",
				UserStatus.ACTIVE,
				PageRequest.of(0, 1)
		);

		assertThat(activeUsers.getTotalElements()).isEqualTo(2);
		assertThat(activeUsers.getContent()).hasSize(1);
		assertThat(adminUserQueryService.search("", null, PageRequest.of(0, 500)).getSize())
				.isEqualTo(100);
	}

	private Page<AdminUserSummaryResponse> search(String keyword, UserStatus status) {
		return adminUserQueryService.search(keyword, status, PageRequest.of(0, 20));
	}

	private User saveUser(String email, String nickname, String name, UserStatus status) {
		return adminUserQueryRepository.saveAndFlush(User.builder()
				.email(email)
				.password("encoded-password")
				.nickname(nickname)
				.name(name)
				.phoneNumber("01012345678")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.status(status)
				.build());
	}
}
