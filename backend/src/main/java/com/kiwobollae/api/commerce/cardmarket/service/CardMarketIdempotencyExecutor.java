package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class CardMarketIdempotencyExecutor {

  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  public <T> T execute(
      Long userId,
      String key,
      String apiType,
      String canonicalRequest,
      Class<T> responseType,
      Function<T, Long> resourceId,
      Supplier<T> action) {
    String hash = hash(canonicalRequest);
    var replay = idempotencyService.replayIfPresent(userId, apiType, key, hash);
    if (replay.isPresent()) {
      return deserialize(replay.get().key().getResponseSnapshot(), responseType);
    }
    IdempotencyExecution execution = idempotencyService.start(userId, apiType, key, hash);
    if (execution.replay()) {
      return deserialize(execution.key().getResponseSnapshot(), responseType);
    }
    T response = action.get();
    idempotencyService.succeed(
        execution.key(), 200, serialize(response), "CARD_MARKET", resourceId.apply(response));
    return response;
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private String serialize(Object response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private <T> T deserialize(String snapshot, Class<T> responseType) {
    try {
      return objectMapper.readValue(snapshot, responseType);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }
}
