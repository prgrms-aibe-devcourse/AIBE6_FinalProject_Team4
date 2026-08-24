package com.kiwobollae.api.inquiry.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.inquiry.entity.Inquiry;
import com.kiwobollae.api.inquiry.entity.enums.InquiryCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_inquiry_test"
						+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
				"spring.jpa.hibernate.ddl-auto=create-drop"
		}
)
class InquiryRepositorySearchMySqlIntegrationTest {

	@Autowired
	private InquiryRepository inquiryRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void setUp() {
		clearData();
	}

	@AfterEach
	void tearDown() {
		clearData();
	}

	@Test
	void searchJoinFetchesUserSoNameIsReadableAfterSessionDetach() {
		User user = userRepository.saveAndFlush(User.builder()
				.email("inquiry-search@example.test")
				.password("encoded-password")
				.nickname("inquiry-search")
				.name("문의검색테스트")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.status(UserStatus.ACTIVE)
				.build());
		inquiryRepository.saveAndFlush(Inquiry.create(user, InquiryCategory.ETC, "제목", "내용"));

		// join fetch가 없으면 clear() 이후 지연 로딩이 끊겨 user 접근 시 예외가 난다 —
		// join fetch가 실제로 적용됐는지는 이렇게 세션에서 분리한 뒤 읽어봐야 확인된다.
		entityManager.clear();
		var result = inquiryRepository.search(null, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).getUser().getName()).isEqualTo("문의검색테스트");
	}

	private void clearData() {
		inquiryRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}
}
