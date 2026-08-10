package com.kiwobollae.api.ai.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlantChatRequest(
    @NotBlank @Size(max = 2000) String question,
    @Size(max = 2000) String currentJournalContent,
    @Valid @Size(max = 6) List<PlantChatMessage> recentMessages) {}
