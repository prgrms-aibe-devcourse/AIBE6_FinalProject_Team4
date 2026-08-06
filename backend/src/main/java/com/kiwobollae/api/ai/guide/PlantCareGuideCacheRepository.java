package com.kiwobollae.api.ai.guide;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantCareGuideCacheRepository extends JpaRepository<PlantCareGuideCache, Long> {

  Optional<PlantCareGuideCache> findBySpeciesNameAndGuideVersionAndSourceContextHash(
      String speciesName, int guideVersion, String sourceContextHash);

  /**
   * 종 이름으로 저장본을 모두 지운다(관리자 캐시 무효화).
   *
   * <p>버전·컨텍스트 해시를 가리지 않고 지운다. 옛 버전이나 옛 원본 컨텍스트로 만들어진 행은 조회에 걸리지 않는 죽은 캐시라 남겨 둘 이유가 없고, 일부만 지우면 "분명
   * 지웠는데 아직 남아 있다"는 혼란만 만든다.
   */
  long deleteBySpeciesName(String speciesName);
}
