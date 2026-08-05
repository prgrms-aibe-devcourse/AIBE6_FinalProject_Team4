package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaDismantleRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaDismantleResponse;
import com.kiwobollae.api.commerce.gacha.entity.CardShardTransaction;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCollection;
import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.repository.CardShardTransactionRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class GachaDismantleServiceTest {

  @Test
  void dismantlesDuplicatesAndLeavesOneCard() {
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaShardWalletService walletService = mock(GachaShardWalletService.class);
    UserCardCollectionRepository collectionRepository = mock(UserCardCollectionRepository.class);
    CardShardTransactionRepository transactionRepository =
        mock(CardShardTransactionRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    GachaDismantleService service =
        new GachaDismantleService(
            idempotencyService,
            walletService,
            collectionRepository,
            transactionRepository,
            userRepository,
            new ObjectMapper());
    IdempotencyKey key = mock(IdempotencyKey.class);
    User user = mock(User.class);
    TradingCard card =
        TradingCard.builder()
            .code("RARE_CARD")
            .name("레어 카드")
            .rarity(TradingCardRarity.RARE)
            .drawWeight(1)
            .displayOrder(1)
            .build();
    ReflectionTestUtils.setField(card, "id", 10L);
    UserCardCollection collection =
        UserCardCollection.builder()
            .user(user)
            .card(card)
            .ownedCount(4)
            .firstAcquiredAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    UserCardShardWallet wallet = new UserCardShardWallet(7L, user, 5L, 5L, 0L, 0L, null, null);

    when(key.getId()).thenReturn(100L);
    when(idempotencyService.replayIfPresent(
            eq(7L), eq("GACHA_CARD_DISMANTLE"), eq("key"), anyString()))
        .thenReturn(Optional.empty());
    when(idempotencyService.start(eq(7L), eq("GACHA_CARD_DISMANTLE"), eq("key"), anyString()))
        .thenReturn(new IdempotencyExecution(key, false));
    when(collectionRepository.findAllByUser_IdAndCard_IdIn(eq(7L), any()))
        .thenReturn(List.of(collection));
    when(walletService.getOrCreateForUpdate(7L)).thenReturn(wallet);
    when(userRepository.getReferenceById(7L)).thenReturn(user);
    when(collectionRepository.decrementKeepingOne(eq(7L), eq(10L), eq(3), any())).thenReturn(1);

    var response =
        service.dismantle(
            7L, "key", new GachaDismantleRequest(List.of(new GachaDismantleRequest.Item(10L, 3))));

    assertThat(response.earnedShards()).isEqualTo(9);
    assertThat(response.balance()).isEqualTo(14);
    assertThat(response.items().getFirst().ownedCountAfter()).isEqualTo(1);
    ArgumentCaptor<CardShardTransaction> captor =
        ArgumentCaptor.forClass(CardShardTransaction.class);
    verify(transactionRepository).save(captor.capture());
    assertThat(captor.getValue().getBalanceAfter()).isEqualTo(14);
  }

  @Test
  void rejectsDuplicateCardIdsBeforeStartingIdempotency() {
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDismantleService service =
        new GachaDismantleService(
            idempotencyService,
            mock(GachaShardWalletService.class),
            mock(UserCardCollectionRepository.class),
            mock(CardShardTransactionRepository.class),
            mock(UserRepository.class),
            new ObjectMapper());

    assertThatThrownBy(
            () ->
                service.dismantle(
                    7L,
                    "key",
                    new GachaDismantleRequest(
                        List.of(
                            new GachaDismantleRequest.Item(10L, 1),
                            new GachaDismantleRequest.Item(10L, 1)))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.GACHA_DISMANTLE_ITEM_INVALID));
  }

  @Test
  void rejectsCardThatUserDoesNotOwnWithDedicatedError() {
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaShardWalletService walletService = mock(GachaShardWalletService.class);
    UserCardCollectionRepository collectionRepository = mock(UserCardCollectionRepository.class);
    GachaDismantleService service =
        new GachaDismantleService(
            idempotencyService,
            walletService,
            collectionRepository,
            mock(CardShardTransactionRepository.class),
            mock(UserRepository.class),
            new ObjectMapper());
    IdempotencyKey key = mock(IdempotencyKey.class);
    when(idempotencyService.replayIfPresent(
            eq(7L), eq("GACHA_CARD_DISMANTLE"), eq("key"), anyString()))
        .thenReturn(Optional.empty());
    when(idempotencyService.start(eq(7L), eq("GACHA_CARD_DISMANTLE"), eq("key"), anyString()))
        .thenReturn(new IdempotencyExecution(key, false));
    when(walletService.getOrCreateForUpdate(7L))
        .thenReturn(new UserCardShardWallet(7L, null, 0L, 0L, 0L, 0L, null, null));
    when(collectionRepository.findAllByUser_IdAndCard_IdIn(7L, List.of(99L))).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.dismantle(
                    7L,
                    "key",
                    new GachaDismantleRequest(List.of(new GachaDismantleRequest.Item(99L, 1)))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GACHA_CARD_NOT_OWNED));
  }

  @Test
  void replaysStoredResponseWithoutDecrementingCardsAgain() throws Exception {
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaShardWalletService walletService = mock(GachaShardWalletService.class);
    UserCardCollectionRepository collectionRepository = mock(UserCardCollectionRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    GachaDismantleService service =
        new GachaDismantleService(
            idempotencyService,
            walletService,
            collectionRepository,
            mock(CardShardTransactionRepository.class),
            mock(UserRepository.class),
            objectMapper);
    IdempotencyKey key = mock(IdempotencyKey.class);
    GachaDismantleResponse stored = new GachaDismantleResponse(3, 13, 13, List.of());
    when(key.getResponseSnapshot()).thenReturn(objectMapper.writeValueAsString(stored));
    when(idempotencyService.replayIfPresent(
            eq(7L), eq("GACHA_CARD_DISMANTLE"), eq("key"), anyString()))
        .thenReturn(Optional.of(new IdempotencyExecution(key, true)));

    GachaDismantleResponse response =
        service.dismantle(
            7L, "key", new GachaDismantleRequest(List.of(new GachaDismantleRequest.Item(1L, 1))));

    assertThat(response).isEqualTo(stored);
    verify(walletService, never()).getOrCreateForUpdate(7L);
    verify(idempotencyService, never()).start(any(), anyString(), anyString(), anyString());
    verify(collectionRepository, never())
        .decrementKeepingOne(any(), any(), any(Integer.class), any());
  }
}
