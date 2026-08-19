package com.kiwobollae.api.ai.policy;

import java.time.Duration;
import java.time.LocalDateTime;
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

  private final AiRateLimitWindowRepository userWindowRepository;
  private final AiUserDailyQuotaWindowRepository userDailyWindowRepository;
  private final AiGlobalRateLimitWindowRepository globalWindowRepository;

  public AiRateLimitStore(
      AiRateLimitWindowRepository userWindowRepository,
      AiUserDailyQuotaWindowRepository userDailyWindowRepository,
      AiGlobalRateLimitWindowRepository globalWindowRepository) {
    this.userWindowRepository = userWindowRepository;
    this.userDailyWindowRepository = userDailyWindowRepository;
    this.globalWindowRepository = globalWindowRepository;
  }

  /**
   * 사용자별 짧은 창·사용자별 하루 총량·전역 예산에서 호출 1건을 함께 예약한다.
   *
   * <p>세 카운터를 하나의 새 트랜잭션에서 처리한다. 전역 예산을 먼저 별도 트랜잭션에서 소비한 뒤 사용자별 제한이 거부되면 실제 외부 호출 없이 전역 예산이 새기
   * 때문이다. 반대로 사용자별 카운터를 먼저 갱신한 뒤 뒤쪽 예산이 거부되면 이 메서드가 예외로 트랜잭션을 롤백해 어느 카운터도 소비되지 않는다.
   *
   * <p>하루 총량 창은 기능을 구분하지 않는다({@link AiUserDailyQuotaWindow} 참고). 기능별로 세면 사용자가 기능을 번갈아 호출해 한도를 기능
   * 수만큼 곱해 쓸 수 있어, 전역 예산을 지킨다는 목적이 무너진다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void consume(
      Long userId,
      AiFeature feature,
      LocalDateTime now,
      Duration userWindow,
      int userMaxRequests,
      Duration userDailyWindow,
      int userDailyMaxRequests,
      Duration globalWindow,
      int globalMaxRequests) {
    // 행이 없을 때만 만든다. 이 존재 확인은 잠금 없는 조회이므로 정상 경로에는 락이
    // 조건부 UPDATE 하나만 남는다. INSERT를 매번 시도하면 이미 있는 행에도 중복 검사용 공유 락을
    // 잡고, 뒤이은 UPDATE가 배타 락을 요구해 동시 요청끼리 락 승격 데드락이 난다.
    if (userWindowRepository.findByUserIdAndFeature(userId, feature).isEmpty()) {
      userWindowRepository.insertWindowIfAbsent(userId, feature.name(), now);
    }
    if (userDailyWindowRepository.findById(userId).isEmpty()) {
      userDailyWindowRepository.insertWindowIfAbsent(userId, now);
    }
    if (globalWindowRepository.findById(AiGlobalRateLimitWindow.GLOBAL_WINDOW_ID).isEmpty()) {
      globalWindowRepository.insertWindowIfAbsent(AiGlobalRateLimitWindow.GLOBAL_WINDOW_ID, now);
    }

    // 모든 경로가 사용자·기능 행 → 사용자 일일 행 → 전역 행 순서로 잠가 순서가 반대인 교착을 만들지
    // 않는다. 전역 행은 짧은 호출 예산 예약에만 쓰이므로 이 직렬화가 AI 요청 자체를 직렬화하지는 않는다.
    if (userWindowRepository.consumeIfAllowed(
            userId, feature, now, now.minus(userWindow), userMaxRequests)
        == 0) {
      throw new AiQuotaExceededException(retryAfterSeconds(userId, feature, now, userWindow));
    }

    // 짧은 창보다 뒤에 둔다. 둘 다 걸리는 경우 먼저 풀리는 쪽의 대기 시간을 돌려줘야 사용자가
    // 불필요하게 오래 기다리지 않는다.
    if (userDailyWindowRepository.consumeIfAllowed(
            userId, now, now.minus(userDailyWindow), userDailyMaxRequests)
        == 0) {
      throw new AiQuotaExceededException(retryAfterSeconds(userId, now, userDailyWindow));
    }

    if (globalWindowRepository.consumeIfAllowed(
            AiGlobalRateLimitWindow.GLOBAL_WINDOW_ID,
            now,
            now.minus(globalWindow),
            globalMaxRequests)
        == 0) {
      throw new AiQuotaExceededException(retryAfterSeconds(now, globalWindow));
    }
  }

  private long retryAfterSeconds(
      Long userId, AiFeature feature, LocalDateTime now, Duration window) {
    LocalDateTime windowStartedAt =
        userWindowRepository
            .findByUserIdAndFeature(userId, feature)
            .map(AiRateLimitWindow::getWindowStartedAt)
            .orElse(now);
    return retryAfterSeconds(windowStartedAt, now, window);
  }

  private long retryAfterSeconds(Long userId, LocalDateTime now, Duration window) {
    LocalDateTime windowStartedAt =
        userDailyWindowRepository
            .findById(userId)
            .map(AiUserDailyQuotaWindow::getWindowStartedAt)
            .orElse(now);
    return retryAfterSeconds(windowStartedAt, now, window);
  }

  private long retryAfterSeconds(LocalDateTime now, Duration window) {
    LocalDateTime windowStartedAt =
        globalWindowRepository
            .findById(AiGlobalRateLimitWindow.GLOBAL_WINDOW_ID)
            .map(AiGlobalRateLimitWindow::getWindowStartedAt)
            .orElse(now);
    return retryAfterSeconds(windowStartedAt, now, window);
  }

  private long retryAfterSeconds(
      LocalDateTime windowStartedAt, LocalDateTime now, Duration window) {
    Duration remaining = Duration.between(now, windowStartedAt.plus(window));
    // 남은 시간이 1초 미만이어도 0을 돌려주면 Retry-After가 무의미해지므로 최소 1초로 올린다.
    return Math.max(1L, (remaining.toMillis() + 999L) / 1_000L);
  }
}
