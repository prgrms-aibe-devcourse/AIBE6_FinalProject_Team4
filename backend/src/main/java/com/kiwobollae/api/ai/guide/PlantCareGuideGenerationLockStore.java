package com.kiwobollae.api.ai.guide;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 캐시 생성 lease를 요청 트랜잭션·외부 AI 호출과 분리해 유지한다. */
@Service
public class PlantCareGuideGenerationLockStore {

  private final PlantCareGuideGenerationLockRepository repository;

  public PlantCareGuideGenerationLockStore(PlantCareGuideGenerationLockRepository repository) {
    this.repository = repository;
  }

  /**
   * 외부 호출 전에 해당 캐시 키의 생성 권한을 선점한다.
   *
   * <p>새 트랜잭션으로 즉시 커밋해야 다른 인스턴스도 선점을 볼 수 있다. 호출 중 프로세스가 종료되면 lease가 만료된 뒤 다음 요청이 회수한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Lease> tryAcquire(
      PlantCareGuideGenerationKey key, LocalDateTime now, Duration leaseDuration) {
    LocalDateTime lockedUntil = now.plus(leaseDuration);
    String ownerToken = UUID.randomUUID().toString();
    repository.acquireIfAvailable(
        key.speciesName(),
        key.guideVersion(),
        key.sourceContextHash(),
        now,
        lockedUntil,
        ownerToken);
    boolean acquired =
        repository
            .findOwnerToken(key.speciesName(), key.guideVersion(), key.sourceContextHash())
            .filter(ownerToken::equals)
            .isPresent();
    if (!acquired) {
      return Optional.empty();
    }
    return Optional.of(new Lease(key, lockedUntil, ownerToken));
  }

  /** 정상·실패 호출 모두 lease를 빨리 반납한다. 실패해도 lease 만료가 최종 안전망이다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void release(Lease lease) {
    repository.deleteOwnedLease(
        lease.key().speciesName(),
        lease.key().guideVersion(),
        lease.key().sourceContextHash(),
        lease.lockedUntil(),
        lease.ownerToken());
  }

  public record Lease(
      PlantCareGuideGenerationKey key, LocalDateTime lockedUntil, String ownerToken) {}
}
