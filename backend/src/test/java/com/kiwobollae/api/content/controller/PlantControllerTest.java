package com.kiwobollae.api.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.content.dto.response.PlantProfileResponse;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import com.kiwobollae.api.content.service.PlantProfileService;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PlantControllerTest {

	@Mock private PlantProfileService plantProfileService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		PlantController plantController = new PlantController(plantProfileService);
		mockMvc = MockMvcBuilders.standaloneSetup(plantController)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(
						new AuthenticationPrincipalArgumentResolver(),
						new PageableHandlerMethodArgumentResolver()
				)
				.build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private void authenticateAs(Long userId) {
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
	}

	private PlantProfileResponse sampleResponse(Long id, PlantStatus status) {
		return new PlantProfileResponse(
				id, 7L, 1L, "바질", "바질이", LocalDate.now(), null, status,
				LocalDateTime.now(), false
		);
	}

	@Test
	void getMyProfilesReturnsPagedListWithoutStatusFilter() throws Exception {
		authenticateAs(7L);
		given(plantProfileService.getMyProfiles(eq(7L), isNull(), any()))
				.willReturn(new PageImpl<>(List.of(sampleResponse(1L, PlantStatus.GROWING)), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/plants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].id").value(1));
	}

	@Test
	void getMyProfilesFiltersByStatus() throws Exception {
		authenticateAs(7L);
		given(plantProfileService.getMyProfiles(eq(7L), eq(PlantStatus.HARVESTED), any()))
				.willReturn(new PageImpl<>(List.of(sampleResponse(2L, PlantStatus.HARVESTED)), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/plants").param("status", "HARVESTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].id").value(2))
				.andExpect(jsonPath("$.data.content[0].status").value("HARVESTED"));
	}

	@Test
	void getMyProfilesRejectsInvalidStatusValue() throws Exception {
		authenticateAs(7L);

		mockMvc.perform(get("/api/v1/plants").param("status", "NOT_A_REAL_STATUS"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void getMyProfilesRejectsPageSizeOverCap() throws Exception {
		authenticateAs(7L);

		mockMvc.perform(get("/api/v1/plants").param("size", "999999"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
	}
}
