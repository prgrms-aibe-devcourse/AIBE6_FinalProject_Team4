package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.commerce.gacha.dto.GachaCardResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaCollectionResponse;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCollection;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawItemRepository;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GachaQueryServiceTest {

  private TradingCardRepository cardRepository;
  private UserCardCollectionRepository collectionRepository;
  private GachaQueryService service;

  @BeforeEach
  void setUp() {
    cardRepository = mock(TradingCardRepository.class);
    collectionRepository = mock(UserCardCollectionRepository.class);
    service =
        new GachaQueryService(
            cardRepository,
            collectionRepository,
            mock(GachaDrawRepository.class),
            mock(GachaDrawItemRepository.class));
    ReflectionTestUtils.setField(service, "assetBaseUrl", "");
  }

  @Test
  void publicCatalogDoesNotExposeOriginalImageUrl() {
    TradingCard card = card(1L, "COMMON_LETTUCE", "양상추");
    when(cardRepository.findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus.ACTIVE))
        .thenReturn(List.of(card));

    List<GachaCardResponse> response = service.getCatalog(null);

    assertThat(response).singleElement().extracting(GachaCardResponse::imageUrl).isNull();
  }

  @Test
  void collectionUnlocksImagesOnlyForCardsAcquiredAtLeastOnce() {
    TradingCard acquired = card(1L, "COMMON_LETTUCE", "양상추");
    TradingCard locked = card(2L, "COMMON_TOMATO", "방울토마토");
    LocalDateTime acquiredAt = LocalDateTime.of(2026, 7, 30, 12, 0);
    UserCardCollection collection =
        UserCardCollection.builder()
            .card(acquired)
            .ownedCount(0)
            .firstAcquiredAt(acquiredAt)
            .updatedAt(acquiredAt)
            .build();

    when(cardRepository.findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus.ACTIVE))
        .thenReturn(List.of(acquired, locked));
    when(collectionRepository.findAllByUser_Id(7L)).thenReturn(List.of(collection));

    List<GachaCollectionResponse> response = service.getCollection(7L);

    assertThat(response)
        .extracting(
            GachaCollectionResponse::id,
            GachaCollectionResponse::unlocked,
            GachaCollectionResponse::owned,
            GachaCollectionResponse::imageUrl)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1L, true, false, "/cards/1/art.png"),
            org.assertj.core.groups.Tuple.tuple(2L, false, false, null));
  }

  @Test
  void collectionIncludesHiddenGoldenCardWhenGachaAcquisitionHistoryRemains() {
    TradingCard hiddenGolden = card(41L, "GOLDEN_CORN", "황금 옥수수");
    ReflectionTestUtils.setField(hiddenGolden, "rarity", TradingCardRarity.GOLDEN_RARE);
    ReflectionTestUtils.setField(hiddenGolden, "status", TradingCardStatus.HIDDEN);
    LocalDateTime acquiredAt = LocalDateTime.of(2026, 7, 30, 12, 0);
    UserCardCollection collection =
        UserCardCollection.builder()
            .card(hiddenGolden)
            .ownedCount(0)
            .firstAcquiredAt(acquiredAt)
            .goldenGachaAcquiredAt(acquiredAt)
            .updatedAt(acquiredAt)
            .build();

    when(cardRepository.findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus.ACTIVE))
        .thenReturn(List.of());
    when(collectionRepository.findAllByUser_Id(7L)).thenReturn(List.of(collection));

    List<GachaCollectionResponse> response = service.getCollection(7L);

    assertThat(response)
        .singleElement()
        .satisfies(
            card -> {
              assertThat(card.id()).isEqualTo(41L);
              assertThat(card.unlocked()).isTrue();
              assertThat(card.owned()).isFalse();
              assertThat(card.goldenGachaAcquired()).isTrue();
              assertThat(card.imageUrl()).isEqualTo("/cards/41/art.png");
            });
  }

  private TradingCard card(Long id, String code, String name) {
    TradingCard card =
        TradingCard.builder()
            .code(code)
            .name(name)
            .rarity(TradingCardRarity.COMMON)
            .description(name + " 설명")
            .imageKey("cards/" + id + "/art.png")
            .drawWeight(98_000)
            .displayOrder(id.intValue())
            .status(TradingCardStatus.ACTIVE)
            .build();
    ReflectionTestUtils.setField(card, "id", id);
    return card;
  }
}
