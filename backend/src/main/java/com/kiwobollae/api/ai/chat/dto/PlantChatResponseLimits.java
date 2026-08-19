package com.kiwobollae.api.ai.chat.dto;

/** 챗봇의 구조화 응답을 800 출력 토큰 예산 안에 유지하기 위한 필드별 상한. */
public final class PlantChatResponseLimits {

  public static final int MAX_ANSWER_LENGTH = 320;
  public static final int MAX_RECOMMENDED_ACTIONS = 2;
  public static final int MAX_ADDITIONAL_CHECKS = 2;
  public static final int MAX_LIST_ITEM_LENGTH = 80;

  private PlantChatResponseLimits() {}
}
