package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaMyCosmeticsResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaShardWalletResponse;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCosmetic;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaCosmeticType;
import com.kiwobollae.api.commerce.gacha.repository.CardShardTransactionRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCosmeticRepository;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GachaCosmeticServiceLockTest {

  private final GachaShardWalletService walletService = mock(GachaShardWalletService.class);
  private final UserCardCosmeticRepository cosmeticRepository =
      mock(UserCardCosmeticRepository.class);
  private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private GachaCosmeticService service;

  @BeforeEach
  void setUp() {
    service =
        new GachaCosmeticService(
            new GachaCosmeticCatalog(),
            walletService,
            cosmeticRepository,
            mock(CardShardTransactionRepository.class),
            idempotencyService,
            mock(UserRepository.class),
            objectMapper);
    when(walletService.getWallet(7L)).thenReturn(new GachaShardWalletResponse(40L, 50L, 10L));
  }

  @Test
  void getMineReadsWalletWithoutCreatingOrLockingIt() {
    when(cosmeticRepository.findAllByUser_Id(7L)).thenReturn(List.of());

    var response = service.getMine(7L);

    assertThat(response.shards().balance()).isEqualTo(40L);
    verify(walletService).getWallet(7L);
    verify(walletService, never()).getOrCreateForUpdate(7L);
  }

  @Test
  void purchaseReplayReturnsSnapshotWithoutLockingWallet() throws Exception {
    IdempotencyKey key = mock(IdempotencyKey.class);
    GachaMyCosmeticsResponse stored =
        new GachaMyCosmeticsResponse(new GachaShardWalletResponse(10L, 40L, 30L), List.of());
    when(key.getResponseSnapshot()).thenReturn(objectMapper.writeValueAsString(stored));
    when(idempotencyService.replayIfPresent(
            eq(7L), eq("GACHA_COSMETIC_PURCHASE"), eq("replay-key"), anyString()))
        .thenReturn(Optional.of(new IdempotencyExecution(key, true)));

    var response = service.purchase(7L, "replay-key", "TITLE_SPROUT_COLLECTOR");

    assertThat(response).isEqualTo(stored);
    verify(walletService, never()).getOrCreateForUpdate(7L);
    verify(idempotencyService, never()).start(eq(7L), anyString(), anyString(), anyString());
  }

  @Test
  void equipReadsWalletWithoutCreatingOrLockingIt() {
    UserCardCosmetic cosmetic = title(null);
    when(cosmeticRepository.findAllByUser_Id(7L)).thenReturn(List.of(cosmetic));
    when(cosmeticRepository.findAllByUser_IdAndCosmeticType(7L, GachaCosmeticType.TITLE))
        .thenReturn(List.of(cosmetic));

    service.equip(7L, "TITLE_SPROUT_COLLECTOR");

    assertThat(cosmetic.getEquippedAt()).isNotNull();
    verify(walletService).getWallet(7L);
    verify(walletService, never()).getOrCreateForUpdate(7L);
  }

  @Test
  void unequipReadsWalletWithoutCreatingOrLockingIt() {
    UserCardCosmetic cosmetic = title(LocalDateTime.now());
    when(cosmeticRepository.findAllByUser_Id(7L)).thenReturn(List.of(cosmetic));
    when(cosmeticRepository.findAllByUser_IdAndCosmeticType(7L, GachaCosmeticType.TITLE))
        .thenReturn(List.of(cosmetic));

    service.unequip(7L, GachaCosmeticType.TITLE);

    assertThat(cosmetic.getEquippedAt()).isNull();
    verify(walletService).getWallet(7L);
    verify(walletService, never()).getOrCreateForUpdate(7L);
  }

  private UserCardCosmetic title(LocalDateTime equippedAt) {
    return UserCardCosmetic.builder()
        .cosmeticCode("TITLE_SPROUT_COLLECTOR")
        .cosmeticType(GachaCosmeticType.TITLE)
        .shardPriceSnapshot(30L)
        .unlockedAt(LocalDateTime.now())
        .equippedAt(equippedAt)
        .build();
  }
}
