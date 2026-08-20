package com.kiwobollae.api.ai.guide;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantCareGuideCacheRepository extends JpaRepository<PlantCareGuideCache, Long> {

  Optional<PlantCareGuideCache> findBySpeciesNameAndGuideVersionAndSourceContextHash(
      String speciesName, int guideVersion, String sourceContextHash);

  // 이미 한 번 이상 생성돼 캐시된 종 이름만 대상으로 하는 부분 일치 검색 — 처음 보는 종은 여기 뜨지 않고
  // 사용자가 이름을 직접 입력해 조회하면 그때 새로 생성된다.
  @Query(
      "select distinct c.speciesName from PlantCareGuideCache c "
          + "where c.speciesName like %:query% order by c.speciesName")
  List<String> findDistinctSpeciesNamesContaining(@Param("query") String query);
}
