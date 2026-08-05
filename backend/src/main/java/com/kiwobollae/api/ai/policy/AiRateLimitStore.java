package com.kiwobollae.api.ai.policy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 호출 제한 카운터를 DB에서 원자적으로 소비한다.
 *
 * <p>호출부 트랜잭션과 분리한다({@code REQUIRES_NEW}). 제한의 목적은 비용·남용 통제이므로 AI 호출이 실패해 호출부가 롤백되어도 그 시도는 소비된 것으로
 * 남아야 한다. 그러지 않으면 실패하는 요청을 반복해 한도 없이 외부 호출을 발생시킬 수 있다.
 *
 * <p>같은 클래스 안에서 호출하면 프록시를 우회해 REQUIRES_NEW가 적용되지 않으므로 {@link AiRequestGuard}와 별도 빈으로 분리했다.
 */
@Component
public class AiRateLimitStore {

  private final AiRateLimitWindowRepository repository;

  public AiRateLimitStore(AiRateLimitWindowRepository repository) {
    this.repository = repository;
  }

  /**
   * 호출 1건을 소비하고, 한도를 넘었으면 남은 대기 시간을 반환한다.
   *
   * @return 소비 성공이면 빈 값, 한도 초과면 재시도까지 남은 초(1 이상)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Long> consume(
      Long userId, AiFeature feature, LocalDateTime now, Duration window, int maxRequests) {
    // 행이 없을 때만 만든다. 이 존재 확인은 잠금 없는 조회이므로 정상 경로에는 락이
    // 조건부 UPDATE 하나만 남는다. INSERT를 매번 시도하면 이미 있는 행에도 중복 검사용 공유 락을
    // 잡고, 뒤이은 UPDATE가 배타 락을 요구해 동시 요청끼리 락 승격 데드락이 난다.
    if (repository.findByUserIdAndFeature(userId, feature).isEmpty()) {
      repository.insertWindowIfAbsent(userId, feature.name(), now);
    }

    if (repository.consumeIfAllowed(userId, feature, now, now.minus(window), maxRequests) == 1) {
      return Optional.empty();
    }

    // 행은 방금 보장했으므로 0건이면 현재 창의 한도를 다 쓴 것이다.
    LocalDateTime windowStartedAt =
        repository
            .findByUserIdAndFeature(userId, feature)
            .map(AiRateLimitWindow::getWindowStartedAt)
            .orElse(now);
    return Optional.of(retryAfterSeconds(windowStartedAt, now, window));
  }

  private long retryAfterSeconds(
      LocalDateTime windowStartedAt, LocalDateTime now, Duration window) {
    Duration remaining = Duration.between(now, windowStartedAt.plus(window));
    // 남은 시간이 1초 미만이어도 0을 돌려주면 Retry-After가 무의미해지므로 최소 1초로 올린다.
    return Math.max(1L, (remaining.toMillis() + 999L) / 1_000L);
  }
}
