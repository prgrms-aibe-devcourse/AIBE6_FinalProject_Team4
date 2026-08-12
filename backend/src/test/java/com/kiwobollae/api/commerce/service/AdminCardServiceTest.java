package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.commerce.dto.request.CardRequest;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.ExchangeProductRepository;
import com.kiwobollae.api.global.asset.AssetUrlResolver;
import com.kiwobollae.api.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AdminCardServiceTest {

  @Mock private CardRepository cardRepository;
  @Mock private ExchangeProductRepository exchangeProductRepository;

  private AdminCardService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminCardService(
            cardRepository,
            exchangeProductRepository,
            new CommerceAssetKeyValidator(),
            new AssetUrlResolver("https://assets.example.com/"),
            org.mockito.Mockito.mock(CommerceAssetStorageService.class));
  }

  @Test
  void listsHiddenCardsForAdmin() {
    ExchangeProduct exchangeProduct = exchangeProduct(3L);
    Card hidden =
        Card.builder()
            .name("숨김 쿠폰")
            .pointPrice(100L)
            .exchangeProduct(exchangeProduct)
            .requiredCountForExchange(3)
            .status(ActiveStatus.HIDDEN)
            .build();
    given(cardRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(hidden));

    assertThat(service.getCards()).singleElement().extracting("status").isEqualTo(ActiveStatus.HIDDEN);
  }

  @Test
  void rejectsUpdateWhenS3PathIdDoesNotMatchCard() {
    Card card =
        Card.builder()
            .name("쿠폰")
            .pointPrice(100L)
            .exchangeProduct(exchangeProduct(3L))
            .requiredCountForExchange(3)
            .status(ActiveStatus.ON_SALE)
            .build();
    given(cardRepository.findByIdForUpdate(10L)).willReturn(Optional.of(card));
    given(exchangeProductRepository.findByIdAndStatus(3L, ActiveStatus.ON_SALE))
        .willReturn(Optional.of(exchangeProduct(3L)));
    CardRequest request =
        new CardRequest(
            "쿠폰",
            100L,
            3L,
            3,
            null,
            "coupons/11/5d085536-b249-56bf-b42f-82e56bd785dd.png",
            ActiveStatus.ON_SALE);

    assertThatThrownBy(() -> service.update(10L, request)).isInstanceOf(BusinessException.class);
  }

  private ExchangeProduct exchangeProduct(Long id) {
    return org.mockito.Mockito.mock(
        ExchangeProduct.class,
        invocation -> {
          if (invocation.getMethod().getName().equals("getId")) return id;
          if (invocation.getMethod().getName().equals("getName")) return "교환 상품";
          return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
  }
}
