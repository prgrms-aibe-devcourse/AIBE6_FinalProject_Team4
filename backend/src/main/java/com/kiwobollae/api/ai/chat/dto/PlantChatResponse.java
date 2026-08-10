package com.kiwobollae.api.ai.chat.dto;

import java.util.List;

public record PlantChatResponse(
    String answer, List<String> recommendedActions, List<String> additionalChecks) {}
