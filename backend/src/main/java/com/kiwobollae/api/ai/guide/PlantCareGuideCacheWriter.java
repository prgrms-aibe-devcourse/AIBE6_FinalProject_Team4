package com.kiwobollae.api.ai.guide;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 캐시 저장을 요청 트랜잭션과 분리한다. 유니크 충돌 시 이 트랜잭션만 롤백되어 재조회가 가능하다. */
@Service
public class PlantCareGuideCacheWriter {

  private final PlantCareGuideCacheRepository repository;

  public PlantCareGuideCacheWriter(PlantCareGuideCacheRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void save(PlantCareGuideCache cache) {
    repository.saveAndFlush(cache);
  }
}
