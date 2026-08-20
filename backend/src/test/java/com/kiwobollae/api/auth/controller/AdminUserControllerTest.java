package com.kiwobollae.api.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.admin.service.AdminUserManagementService;
import com.kiwobollae.api.auth.dto.response.AdminUserSummaryResponse;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.service.AdminUserQueryService;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

	@Mock private AdminUserQueryService adminUserQueryService;
	@Mock private AdminUserManagementService adminUserManagementService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
						new AdminUserController(adminUserQueryService, adminUserManagementService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
				.build();
	}

	@Test
	void getUsersReturnsSearchablePagedList() throws Exception {
		AdminUserSummaryResponse user = new AdminUserSummaryResponse(
				7L,
				"green@example.com",
				"초록",
				"김초록",
				UserRole.USER,
				UserStatus.ACTIVE,
				null,
				null,
				LocalDateTime.of(2026, 8, 1, 10, 0),
				3L
		);
		given(adminUserQueryService.search(eq("초록"), eq(UserStatus.ACTIVE), any()))
				.willReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/admin/user")
						.param("keyword", "초록")
						.param("status", "ACTIVE")
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].id").value(7))
				.andExpect(jsonPath("$.data.content[0].email").value("green@example.com"))
				.andExpect(jsonPath("$.data.content[0].nickname").value("초록"))
				.andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.content[0].reportCount").value(3))
				.andExpect(jsonPath("$.data.content[0].phoneNumber").doesNotExist());
	}
}
