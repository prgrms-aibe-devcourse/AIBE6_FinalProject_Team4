package com.kiwobollae.api.ai.chat.dto;

import java.util.List;
import java.util.UUID;

public record PlantChatResponse(
    UUID conversationId,
    String answer,
    List<String> recommendedActions,
    List<String> additionalChecks) {}
