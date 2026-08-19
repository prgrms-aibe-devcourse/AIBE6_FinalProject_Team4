package com.kiwobollae.api.ai.chat.dto;

/** 식물 챗봇이 제공하는 유한한 제품 기능 범위. 범위 밖·불확실 판정에는 NONE을 사용한다. */
public enum PlantChatScopeIntent {
  CARE,
  GROWTH_OBSERVATION,
  JOURNAL_INTERPRETATION,
  DIRECT_FOLLOW_UP,
  NONE
}
