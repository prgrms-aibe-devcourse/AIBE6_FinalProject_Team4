package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.response.GachaPackProductQuote;
import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseResponse;
import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.commerce.service.ProductService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class GachaPackPurchaseService {

  private static final String API_TYPE = "GACHA_PACK_PURCHASE";

  private final ProductService productService;
  private final WalletService walletService;
  private final IdempotencyService idempotencyService;
  private final GachaDrawRepository gachaDrawRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @Transactional
  public GachaPackPurchaseResponse purchase(
      Long userId, String idempotencyKey, GachaPackPurchaseRequest request) {
    validate(userId, idempotencyKey, request);
    IdempotencyExecution execution =
        idempotencyService.start(
            userId,
            API_TYPE,
            idempotencyKey,
            sha256(request.productId() + ":" + request.quantity()));
    if (execution.replay()) {
      return deserialize(execution.key().getResponseSnapshot());
    }

    GachaPackProductQuote product = productService.getActiveGachaPack(request.productId());
    if (request.quantity() > product.maxQuantity()) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    long totalPoint;
    try {
      totalPoint = Math.multiplyExact(product.unitPoint(), request.quantity().longValue());
    } catch (ArithmeticException exception) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }

    PointDeductionResult pointUsage =
        walletService.deductForGachaPurchase(userId, totalPoint, execution.key().getId());
    Long drawId = reservePack(userId, execution.key().getId());
    GachaPackPurchaseResponse response =
        new GachaPackPurchaseResponse(
            execution.key().getId(),
            product.productId(),
            product.name(),
            request.quantity(),
            product.unitPoint(),
            totalPoint,
            pointUsage.usedFreePoint(),
            pointUsage.usedPaidPoint(),
            pointUsage.remainingBalance(),
            List.of(drawId));
    idempotencyService.succeed(
        execution.key(), 200, serialize(response), "GACHA_PACK_PURCHASE", execution.key().getId());
    return response;
  }

  private Long reservePack(Long userId, Long purchaseId) {
    User user = userRepository.getReferenceById(userId);
    GachaDraw draw =
        gachaDrawRepository.saveAndFlush(
            GachaDraw.builder()
                .user(user)
                .sourceType(GachaSourceType.PURCHASE)
                .sourceId(purchaseId)
                .status(GachaDrawStatus.PENDING)
                .drawCount(5)
                .rateVersion(1)
                .build());
    eventPublisher.publishEvent(new GachaRewardCreatedEvent(draw.getId()));
    return draw.getId();
  }

  private void validate(Long userId, String idempotencyKey, GachaPackPurchaseRequest request) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    if (request == null
        || request.productId() == null
        || request.quantity() == null
        || request.quantity() != GachaPackProductQuote.MAX_PURCHASE_QUANTITY) {
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

  private String serialize(GachaPackPurchaseResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private GachaPackPurchaseResponse deserialize(String snapshot) {
    try {
      return objectMapper.readValue(snapshot, GachaPackPurchaseResponse.class);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }
}
