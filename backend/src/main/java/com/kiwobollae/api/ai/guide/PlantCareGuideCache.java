package com.kiwobollae.api.ai.guide;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 종별 재배 가이드 캐시.
 *
 * <p>가이드는 사용자별 콘텐츠가 아니다 — "방울토마토 재배 가이드"는 누가 요청해도 같은 내용이다. 그래서 종당 한 번만 생성해 저장하고 이후 요청은 저장본을 그대로
 * 돌려준다. 종이 열여섯 개면 AI 호출은 사용자 수와 무관하게 최대 열여섯 번이다.
 *
 * <p><b>캐시 키는 species_id가 아니라 정규화된 종 이름·응답 버전·원본 컨텍스트 fingerprint다.</b> 앞으로 사용자가 DB에 없는 종을 직접 입력하는
 * 경우까지 같은 저장소로 덮기 위한 선택이다. 등록된 종은 source_species_id에 출처를 남기지만 물리 FK는 두지 않는다(임의 입력 종은 가리킬 행이 없고, 종이
 * 삭제돼도 가이드는 남는 편이 낫다).
 */
@Getter
@Entity
@Table(
    name = "ai_plant_care_guides",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_ai_plant_care_guide_species_name_version",
          columnNames = {"species_name", "guide_version", "source_context_hash"})
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlantCareGuideCache extends BaseEntity {

  /** 정규화된 종 이름(캐시 키). 정규화 규칙은 PlantCareGuideService#normalizeSpeciesName 참고. */
  @Column(name = "species_name", nullable = false, length = 100)
  private String speciesName;

  /** 등록된 종에서 생성됐다면 그 id. 사용자가 직접 입력한 종이면 null. 논리 참조, 물리 FK 없음. */
  @Column(name = "source_species_id")
  private Long sourceSpeciesId;

  /**
   * 생성 시점에 사용한 종 분류·공식 가이드 fingerprint. 원본 변경 시 기존 캐시를 무효화한다.
   *
   * <p><b>NOT NULL이어야 한다.</b> MySQL 유니크 인덱스는 NULL끼리를 서로 다른 값으로 취급해서, 이 컬럼이 비면 위 유니크 제약이 같은 종·같은 버전의
   * 중복 행을 전혀 막지 못한다. 분류·공식 가이드가 없는 종도 빈 문자열을 해싱해 값이 항상 채워진다.
   */
  @Column(name = "source_context_hash", nullable = false, length = 64)
  private String sourceContextHash;

  /** 응답 스키마 버전. 스키마를 바꾸면 이 값을 올려 옛 저장본을 자연히 무효화한다 — 지우거나 마이그레이션 하지 않고 새 버전으로만 조회하면 된다. */
  @Column(name = "guide_version", nullable = false)
  private int guideVersion;

  /** 생성에 사용된 모델. 품질 문제를 추적할 때 어떤 모델의 산출물인지 알아야 한다. */
  @Column(nullable = false, length = 100)
  private String model;

  /**
   * AI가 반환한 가이드 JSON 원본.
   *
   * <p>가이드는 읽기 전용 문서라 컬럼으로 쪼갤 이유가 없다. 통째로 저장하면 응답 스키마가 바뀌어도 테이블 구조는 그대로 두고 guide_version만 올리면 되고,
   * 원본 종 정보가 바뀌면 source_context_hash가 기존 저장본을 무효화한다.
   */
  @Lob
  @Column(name = "guide_json", nullable = false)
  private String guideJson;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
