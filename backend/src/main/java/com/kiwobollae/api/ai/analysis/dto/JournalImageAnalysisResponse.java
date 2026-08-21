package com.kiwobollae.api.ai.analysis.dto;

import com.kiwobollae.api.ai.knowledge.PlantCareGrounding;
import java.time.LocalDateTime;
import java.util.List;

public record JournalImageAnalysisResponse(
    Long id,
    Long journalId,
    String imageHash,
    JournalImageAnalysisResult.ImageQuality imageQuality,
    JournalImageAnalysisResult.PlantCondition condition,
    String summary,
    List<String> observations,
    List<String> possibleCauses,
    List<String> recommendedActions,
    List<String> additionalChecks,
    PlantCareGrounding grounding,
    LocalDateTime analyzedAt) {}
