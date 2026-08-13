package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaTestCardGrantRequest;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.GoldenOriginType;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalGachaTestCardGrantServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final TradingCardRepository cardRepository = mock(TradingCardRepository.class);
  private final GoldenCardInstanceRepository goldenRepository =
      mock(GoldenCardInstanceRepository.class);
  private final GachaCollectionAcquisitionService acquisitionService =
      mock(GachaCollectionAcquisitionService.class);
  private final GachaRandomSource randomSource = mock(GachaRandomSource.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC);
  private LocalGachaTestCardGrantService service;

  @BeforeEach
  void setUp() {
    service =
        new LocalGachaTestCardGrantService(
            userRepository,
            cardRepository,
            goldenRepository,
            acquisitionService,
            randomSource,
            clock);
  }

  @Test
  void grantsTwoCopiesOfSameRandomHyperCardForSaleTesting() {
    User user = mock(User.class);
    TradingCard card = mock(TradingCard.class);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(cardRepository.findAllByStatusAndRarityOrderByDisplayOrderAsc(
            TradingCardStatus.ACTIVE, TradingCardRarity.HYPER_RARE))
        .thenReturn(List.of(card));
    when(randomSource.nextInt(1)).thenReturn(0);
    when(card.getId()).thenReturn(11L);
    when(card.getName()).thenReturn("애플망고");
    when(acquisitionService.acquireNormal(
            7L, 11L, clock.instant().atOffset(ZoneOffset.UTC).toLocalDateTime()))
        .thenReturn(1, 2);

    var result = service.grant(7L, new GachaTestCardGrantRequest(TradingCardRarity.HYPER_RARE, 2));

    assertThat(result.cardId()).isEqualTo(11L);
    assertThat(result.grantedQuantity()).isEqualTo(2);
    assertThat(result.ownedCountAfter()).isEqualTo(2);
    verify(acquisitionService, times(2))
        .acquireNormal(7L, 11L, clock.instant().atOffset(ZoneOffset.UTC).toLocalDateTime());
    verify(goldenRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void grantsGoldenCardAsTradableAdminOriginInstance() {
    User user = mock(User.class);
    TradingCard card = mock(TradingCard.class);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(cardRepository.findAllByStatusAndRarityOrderByDisplayOrderAsc(
            TradingCardStatus.ACTIVE, TradingCardRarity.GOLDEN_RARE))
        .thenReturn(List.of(card));
    when(randomSource.nextInt(1)).thenReturn(0);
    when(card.getId()).thenReturn(21L);
    when(card.getName()).thenReturn("황금 옥수수");
    when(acquisitionService.acquireNormal(
            7L, 21L, clock.instant().atOffset(ZoneOffset.UTC).toLocalDateTime()))
        .thenReturn(1);
    var result = service.grant(7L, new GachaTestCardGrantRequest(TradingCardRarity.GOLDEN_RARE, 1));

    assertThat(result.ownedCountAfter()).isEqualTo(1);
    ArgumentCaptor<GoldenCardInstance> instanceCaptor =
        ArgumentCaptor.forClass(GoldenCardInstance.class);
    verify(goldenRepository).save(instanceCaptor.capture());
    GoldenCardInstance instance = instanceCaptor.getValue();
    assertThat(instance.getOriginType()).isEqualTo(GoldenOriginType.ADMIN);
    assertThat(instance.getGoldenOriginRank()).isNull();
    assertThat(instance.getOwnerUser()).isSameAs(user);
    assertThat(instance.getOriginUser()).isSameAs(user);
  }
}
