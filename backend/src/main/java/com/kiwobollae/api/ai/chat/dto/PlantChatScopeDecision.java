package com.kiwobollae.api.ai.chat.dto;

/** 식물 챗봇 질문의 의미 기반 허용 범위 판정. 불확실하면 답변하지 않는다. */
public enum PlantChatScopeDecision {
  ANSWER,
  OTHER_PLANT,
  REFUSE,
  UNCERTAIN
}
