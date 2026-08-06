package com.kiwobollae.api.ai.guide;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 같은 캐시 키의 AI 생성을 한 요청으로 제한하는 프로세스 내 선점 장치.
 *
 * <p><b>인스턴스 안에서만 유효하다.</b> 이 서비스는 EC2 한 대에서 컨테이너 하나로 돌고 Blue/Green 전환 순간에만 잠시 둘이 겹친다. 그 몇 초 동안은 중복
 * 호출이 날 수 있지만 전역 호출 한도가 총량 상한을 잡으므로 감수한다. 인스턴스를 둘 이상으로 늘리면 이 방식은 무력해지고 공유 저장소 기반 락으로 돌아가야 한다.
 *
 * <p>호출 제한 카운터를 DB에 두는 것(`ai_rate_limit_windows`)과는 판단이 다르다. 카운터는 리셋되면 예산 자체가 날아가지만, 이 선점은 사라져도 "가끔
 * 두 번 부른다"로 끝난다.
 */
@Service
public class PlantCareGuideGenerationLockStore {

  /**
   * 생성이 진행 중인 키만 들어 있다.
   *
   * <p>선점할 때 넣고 반납할 때 지우므로 크기가 종 개수가 아니라 <b>동시에 생성 중인 요청 수</b>로 제한된다. 종이나 원본 컨텍스트가 늘어도 쌓이지 않는다.
   */
  private final Map<PlantCareGuideGenerationKey, Object> inFlight = new ConcurrentHashMap<>();

  /**
   * 외부 호출 전에 해당 캐시 키의 생성 권한을 선점한다.
   *
   * <p>{@code putIfAbsent}가 원자적이라 같은 키로 동시에 들어와도 한 요청만 성공한다. 선점하지 못한 요청은 <b>기다리지 않는다</b> — AI 호출이
   * 수십 초 걸려서, 대기시키면 그만큼 응답이 늘어지고 커넥션도 붙잡는다.
   */
  public Optional<Lease> tryAcquire(PlantCareGuideGenerationKey key) {
    Object ownerToken = new Object();
    if (inFlight.putIfAbsent(key, ownerToken) != null) {
      return Optional.empty();
    }
    return Optional.of(new Lease(key, ownerToken));
  }

  /**
   * 선점을 반납한다. 정상·실패 호출 모두 즉시 반납한다.
   *
   * <p>자기 token이 아직 들어 있을 때만 지운다. 값까지 비교하는 {@code remove(key, value)}라 남의 선점을 지울 수 없다.
   */
  public void release(Lease lease) {
    inFlight.remove(lease.key(), lease.ownerToken());
  }

  public record Lease(PlantCareGuideGenerationKey key, Object ownerToken) {}
}
