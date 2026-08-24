package com.kiwobollae.api.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.inquiry.dto.response.InquiryResponse;
import com.kiwobollae.api.inquiry.entity.Inquiry;
import com.kiwobollae.api.inquiry.entity.enums.InquiryCategory;
import com.kiwobollae.api.inquiry.repository.InquiryRepository;
import com.kiwobollae.api.notification.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

	@Mock private InquiryRepository inquiryRepository;
	@Mock private UserRepository userRepository;
	@Mock private NotificationService notificationService;

	@InjectMocks
	private InquiryService inquiryService;

	@Test
	void getInquiriesForAdminIncludesUserName() {
		User user = User.builder().email("user@test.com").nickname("초록").name("김초록").build();
		ReflectionTestUtils.setField(user, "id", 7L);
		Inquiry inquiry = Inquiry.create(user, InquiryCategory.ETC, "제목", "내용");
		ReflectionTestUtils.setField(inquiry, "id", 1L);
		Pageable pageable = PageRequest.of(0, 20);
		given(inquiryRepository.search(null, pageable)).willReturn(new PageImpl<>(List.of(inquiry)));

		Page<InquiryResponse> result = inquiryService.getInquiriesForAdmin(null, pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).userName()).isEqualTo("김초록");
	}
}
