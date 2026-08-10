package com.kiwobollae.api.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlantChatMessage(
    @NotNull PlantChatRole role, @NotBlank @Size(max = 1000) String content) {}
