package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaCosmeticResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaMyCosmeticsResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaShardWalletResponse;
import com.kiwobollae.api.commerce.gacha.entity.CardShardTransaction;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCosmetic;
import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import com.kiwobollae.api.commerce.gacha.entity.enums.CardShardTransactionType;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaCosmeticType;
import com.kiwobollae.api.commerce.gacha.repository.CardShardTransactionRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCosmeticRepository;
import com.kiwobollae.api.commerce.gacha.service.GachaCosmeticCatalog.CosmeticDefinition;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class GachaCosmeticService {

  private static final String API_TYPE = "GACHA_COSMETIC_PURCHASE";

  private final GachaCosmeticCatalog catalog;
  private final GachaShardWalletService walletService;
  private final UserCardCosmeticRepository cosmeticRepository;
  private final CardShardTransactionRepository transactionRepository;
  private final IdempotencyService idempotencyService;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<GachaCosmeticResponse> getCatalog() {
    return catalog.all().stream()
        .map(definition -> GachaCosmeticResponse.from(definition, null))
        .toList();
  }

  @Transactional(readOnly = true)
  public GachaMyCosmeticsResponse getMine(Long userId) {
    requireUser(userId);
    return mine(userId, walletService.getWallet(userId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public GachaMyCosmeticsResponse purchase(Long userId, String idempotencyKey, String code) {
    requireUser(userId);
    validateKey(idempotencyKey);
    CosmeticDefinition definition = catalog.get(code);
    String hash = sha256(definition.code());
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

    if (cosmeticRepository.existsByUser_IdAndCosmeticCode(userId, definition.code())) {
      throw new BusinessException(ErrorCode.GACHA_COSMETIC_ALREADY_OWNED);
    }
    if (!wallet.spend(definition.price())) {
      throw new BusinessException(ErrorCode.GACHA_SHARD_INSUFFICIENT_BALANCE);
    }

    LocalDateTime now = LocalDateTime.now(KST);
    User user = userRepository.getReferenceById(userId);
    cosmeticRepository.save(
        UserCardCosmetic.builder()
            .user(user)
            .cosmeticCode(definition.code())
            .cosmeticType(definition.type())
            .shardPriceSnapshot(definition.price())
            .unlockedAt(now)
            .build());
    transactionRepository.save(
        CardShardTransaction.builder()
            .user(user)
            .transactionType(CardShardTransactionType.COSMETIC_SPEND)
            .cosmeticCode(definition.code())
            .amount(-definition.price())
            .balanceAfter(wallet.getBalance())
            .requestId(execution.key().getId())
            .lineNo(1)
            .createdAt(now)
            .build());
    GachaMyCosmeticsResponse response = mine(userId, GachaShardWalletResponse.from(wallet));
    idempotencyService.succeed(
        execution.key(), 200, serialize(response), "CARD_COSMETIC", execution.key().getId());
    return response;
  }

  @Transactional
  public GachaMyCosmeticsResponse equip(Long userId, String code) {
    requireUser(userId);
    CosmeticDefinition definition = catalog.get(code);
    UserCardCosmetic target =
        cosmeticRepository.findAllByUser_Id(userId).stream()
            .filter(cosmetic -> cosmetic.getCosmeticCode().equals(code))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_COSMETIC_NOT_OWNED));
    if (target.getEquippedAt() == null) {
      cosmeticRepository
          .findAllByUser_IdAndCosmeticType(userId, definition.type())
          .forEach(UserCardCosmetic::unequip);
      target.equip(LocalDateTime.now(KST));
    }
    return mine(userId, walletService.getWallet(userId));
  }

  @Transactional
  public GachaMyCosmeticsResponse unequip(Long userId, GachaCosmeticType type) {
    requireUser(userId);
    if (type == null) {
      throw new BusinessException(ErrorCode.GACHA_COSMETIC_NOT_FOUND);
    }
    cosmeticRepository.findAllByUser_IdAndCosmeticType(userId, type).stream()
        .filter(cosmetic -> cosmetic.getEquippedAt() != null)
        .forEach(UserCardCosmetic::unequip);
    return mine(userId, walletService.getWallet(userId));
  }

  private GachaMyCosmeticsResponse mine(Long userId, GachaShardWalletResponse shards) {
    Map<String, UserCardCosmetic> owned =
        cosmeticRepository.findAllByUser_Id(userId).stream()
            .collect(Collectors.toMap(UserCardCosmetic::getCosmeticCode, Function.identity()));
    List<GachaCosmeticResponse> cosmetics =
        catalog.all().stream()
            .map(definition -> GachaCosmeticResponse.from(definition, owned.get(definition.code())))
            .toList();
    return new GachaMyCosmeticsResponse(shards, cosmetics);
  }

  private void requireUser(Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
  }

  private void validateKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private String serialize(GachaMyCosmeticsResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private GachaMyCosmeticsResponse deserialize(String snapshot) {
    try {
      return objectMapper.readValue(snapshot, GachaMyCosmeticsResponse.class);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }
}
