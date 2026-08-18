package com.kiwobollae.api.ai.chat.dto;

import java.util.List;

/** AI structured output 전용 응답. conversationId는 서버가 별도로 부여한다. */
public record PlantChatGeneratedResponse(
    PlantChatScopeDecision scopeDecision,
    PlantChatScopeIntent scopeIntent,
    String answer,
    List<String> recommendedActions,
    List<String> additionalChecks) {}
