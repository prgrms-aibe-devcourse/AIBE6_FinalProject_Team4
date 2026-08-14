package com.kiwobollae.api.point.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import com.kiwobollae.api.point.dto.response.PointActivityResponse;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.service.PointTransactionService;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class PointControllerTest {

	@Mock private WalletService walletService;
	@Mock private PointTransactionService pointTransactionService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		PointController controller = new PointController(walletService, pointTransactionService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(
						new UserArgumentResolver(),
						new PageableHandlerMethodArgumentResolver()
				)
				.build();
	}

	@Test
	void getActivitiesReturnsGroupedPointHistory() throws Exception {
		PointActivityResponse activity = new PointActivityResponse(
				12L,
				PointTxType.PURCHASE,
				PointRefType.ORDER,
				10L,
				null,
				-1_000L,
				-700L,
				-300L,
				2_000L,
				500L,
				LocalDateTime.of(2026, 8, 3, 10, 0)
		);
		given(pointTransactionService.getActivities(
				eq(7L),
				isNull(),
				eq(PointRefType.ORDER),
				isNull(),
				isNull(),
				any()
		)).willReturn(new PageImpl<>(List.of(activity), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/points/activities")
						.param("refType", "ORDER")
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].type").value("PURCHASE"))
				.andExpect(jsonPath("$.data.content[0].refType").value("ORDER"))
				.andExpect(jsonPath("$.data.content[0].amount").value(-1000))
				.andExpect(jsonPath("$.data.content[0].paidAmount").value(-700))
				.andExpect(jsonPath("$.data.content[0].freeAmount").value(-300));
	}

	private static final class UserArgumentResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
		}

		@Override
		public Object resolveArgument(
				MethodParameter parameter,
				ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest,
				org.springframework.web.bind.support.WebDataBinderFactory binderFactory
		) {
			return 7L;
		}
	}
}
