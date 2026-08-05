package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaDismantleRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaDismantleResponse;
import com.kiwobollae.api.commerce.gacha.entity.CardShardTransaction;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCollection;
import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import com.kiwobollae.api.commerce.gacha.entity.enums.CardShardTransactionType;
import com.kiwobollae.api.commerce.gacha.repository.CardShardTransactionRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class GachaDismantleService {

  private static final String API_TYPE = "GACHA_CARD_DISMANTLE";
  private static final int MAX_ITEM_TYPES = 20;

  private final IdempotencyService idempotencyService;
  private final GachaShardWalletService walletService;
  private final UserCardCollectionRepository collectionRepository;
  private final CardShardTransactionRepository transactionRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public GachaDismantleResponse dismantle(
      Long userId, String idempotencyKey, GachaDismantleRequest request) {
    List<GachaDismantleRequest.Item> items = validateAndSort(userId, idempotencyKey, request);
    String hash = requestHash(items);
    var replay = idempotencyService.replayIfPresent(userId, API_TYPE, idempotencyKey, hash);
    if (replay.isPresent()) {
      return deserialize(replay.get().key().getResponseSnapshot());
    }
    UserCardShardWallet wallet = walletService.getOrCreateForUpdate(userId);
    IdempotencyExecution execution =
        idempotencyService.start(userId, API_TYPE, idempotencyKey, hash);
    if (execution.replay()) {
      return deserialize(execution.key().getResponseSnapshot());
    }

    List<Long> cardIds = items.stream().map(GachaDismantleRequest.Item::cardId).toList();
    Map<Long, UserCardCollection> collections = new HashMap<>();
    collectionRepository
        .findAllByUser_IdAndCard_IdIn(userId, cardIds)
        .forEach(collection -> collections.put(collection.getCard().getId(), collection));
    if (collections.size() != cardIds.size()) {
      throw new BusinessException(ErrorCode.GACHA_CARD_NOT_OWNED);
    }

    User user = userRepository.getReferenceById(userId);
    LocalDateTime now = LocalDateTime.now(KST);
    List<PendingLine> pending = new ArrayList<>();
    long total = 0;
    for (GachaDismantleRequest.Item item : items) {
      UserCardCollection collection = collections.get(item.cardId());
      int shardPerCard = GachaShardPolicy.shardPerCard(collection.getCard().getRarity());
      if (shardPerCard == 0) {
        throw new BusinessException(ErrorCode.GACHA_CARD_NOT_DISMANTLABLE);
      }
      if (collection.getOwnedCount() < item.quantity() + 1) {
        throw new BusinessException(ErrorCode.GACHA_CARD_KEEP_ONE_REQUIRED);
      }
      long earned = Math.multiplyExact((long) shardPerCard, item.quantity());
      total = Math.addExact(total, earned);
      pending.add(new PendingLine(collection, item.quantity(), shardPerCard, earned));
    }

    List<GachaDismantleResponse.Item> responseItems = new ArrayList<>();
    long runningBalance = wallet.getBalance();
    int lineNo = 1;
    for (PendingLine line : pending) {
      int updated =
          collectionRepository.decrementKeepingOne(
              userId, line.collection().getCard().getId(), line.quantity(), now);
      if (updated == 0) {
        throw new BusinessException(ErrorCode.GACHA_CARD_KEEP_ONE_REQUIRED);
      }
      runningBalance = Math.addExact(runningBalance, line.earned());
      transactionRepository.save(
          CardShardTransaction.builder()
              .user(user)
              .transactionType(CardShardTransactionType.DISMANTLE_EARN)
              .card(line.collection().getCard())
              .cardQuantity(line.quantity())
              .shardPerCardSnapshot((long) line.shardPerCard())
              .amount(line.earned())
              .balanceAfter(runningBalance)
              .requestId(execution.key().getId())
              .lineNo(lineNo++)
              .createdAt(now)
              .build());
      responseItems.add(
          new GachaDismantleResponse.Item(
              line.collection().getCard().getId(),
              line.collection().getCard().getName(),
              line.quantity(),
              line.shardPerCard(),
              line.earned(),
              line.collection().getOwnedCount() - line.quantity()));
    }
    wallet.earn(total);
    GachaDismantleResponse response =
        new GachaDismantleResponse(
            total, wallet.getBalance(), wallet.getLifetimeEarned(), responseItems);
    idempotencyService.succeed(
        execution.key(), 200, serialize(response), "CARD_SHARD_DISMANTLE", execution.key().getId());
    return response;
  }

  private List<GachaDismantleRequest.Item> validateAndSort(
      Long userId, String idempotencyKey, GachaDismantleRequest request) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    if (request == null
        || request.items() == null
        || request.items().isEmpty()
        || request.items().size() > MAX_ITEM_TYPES) {
      throw new BusinessException(ErrorCode.GACHA_DISMANTLE_ITEM_INVALID);
    }
    HashSet<Long> ids = new HashSet<>();
    for (GachaDismantleRequest.Item item : request.items()) {
      if (item == null
          || item.cardId() == null
          || item.cardId() < 1
          || item.quantity() == null
          || item.quantity() < 1
          || item.quantity() > 999
          || !ids.add(item.cardId())) {
        throw new BusinessException(ErrorCode.GACHA_DISMANTLE_ITEM_INVALID);
      }
    }
    return request.items().stream()
        .sorted(Comparator.comparing(GachaDismantleRequest.Item::cardId))
        .toList();
  }

  private String requestHash(List<GachaDismantleRequest.Item> items) {
    String canonical =
        items.stream()
            .map(item -> item.cardId() + ":" + item.quantity())
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private String serialize(GachaDismantleResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private GachaDismantleResponse deserialize(String snapshot) {
    try {
      return objectMapper.readValue(snapshot, GachaDismantleResponse.class);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private record PendingLine(
      UserCardCollection collection, int quantity, int shardPerCard, long earned) {}
}
