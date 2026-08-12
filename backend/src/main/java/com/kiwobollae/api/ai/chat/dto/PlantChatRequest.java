package com.kiwobollae.api.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PlantChatRequest(@NotBlank @Size(max = 2000) String question, UUID conversationId) {}
