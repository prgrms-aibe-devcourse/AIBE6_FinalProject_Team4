package com.kiwobollae.api.ai.policy;

/**
 * AI 기능 구분. 호출 제한을 이 단위로 따로 센다.
 *
 * <p>값은 ai_rate_limit_windows.feature에 문자열로 저장된다. 컬럼을 varchar로 못박아 두었으므로 값을 추가·변경할 때 DDL 작업이 필요
 * 없다({@link AiRateLimitWindow} 참고).
 */
public enum AiFeature {
  /** 사용자가 고른 종 하나에 대한 재배 가이드 생성. */
  PLANT_CARE_GUIDE,
  PLANT_CHAT,
  JOURNAL_IMAGE_ANALYSIS
}
