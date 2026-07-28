package com.kiwobollae.api.commerce.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.commerce.dto.response.CardResponse;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.service.CardService;
import com.kiwobollae.api.commerce.service.CardPurchaseService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CardControllerTest {

	@Mock
	private CardService cardService;

	@Mock
	private CardPurchaseService cardPurchaseService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		CardController cardController = new CardController(cardService, cardPurchaseService);
		mockMvc = MockMvcBuilders.standaloneSetup(cardController)
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void anonymousCardListReturnsCardsWithoutOwnedCount() throws Exception {
		given(cardService.getCards(null)).willReturn(List.of(cardResponse(null)));

		mockMvc.perform(get("/api/v1/card"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data[0].id").value(1))
				.andExpect(jsonPath("$.data[0].status").value("ON_SALE"))
				.andExpect(jsonPath("$.data[0].exchangeProductName").value("수박 한 통"))
				.andExpect(jsonPath("$.data[0].ownedCount").doesNotExist());

		verify(cardService).getCards(null);
	}

	@Test
	void cardDetailReturnsCardNotFoundError() throws Exception {
		given(cardService.getCard(404L, null))
				.willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND));

		mockMvc.perform(get("/api/v1/card/404"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("카드를 찾을 수 없습니다."));
	}

	@Test
	void cardDetailRejectsNonNumericCardId() throws Exception {
		mockMvc.perform(get("/api/v1/card/not-a-number"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void cardDetailRejectsNonPositiveCardId() throws Exception {
		mockMvc.perform(get("/api/v1/card/0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
	}

	private CardResponse cardResponse(Integer ownedCount) {
		return new CardResponse(
				1L,
				"수박 카드",
				300L,
				11L,
				"수박 한 통",
				"제철 수박",
				null,
				10,
				5,
				"수박 교환 카드",
				null,
				ActiveStatus.ON_SALE,
				null,
				null,
				ownedCount
		);
	}
}
