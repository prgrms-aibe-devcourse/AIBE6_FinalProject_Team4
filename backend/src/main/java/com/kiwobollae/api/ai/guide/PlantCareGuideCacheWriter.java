package com.kiwobollae.api.ai.guide;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 캐시 쓰기(저장·삭제)를 요청 트랜잭션과 분리한다. 유니크 충돌 시 이 트랜잭션만 롤백되어 재조회가 가능하다. */
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

  /**
   * 종 이름에 해당하는 저장본을 모두 지우고 지운 개수를 돌려준다.
   *
   * <p>삭제만 짧은 트랜잭션으로 끊는다. 강제 재생성은 삭제 뒤에 수십 초가 걸릴 수 있는 외부 AI 호출이 이어지므로, 한 트랜잭션에 묶으면 그동안 DB 커넥션과 행
   * 잠금을 붙들고 있게 된다.
   */
  @Transactional
  public long deleteAllBySpeciesName(String speciesName) {
    return repository.deleteBySpeciesName(speciesName);
  }
}
