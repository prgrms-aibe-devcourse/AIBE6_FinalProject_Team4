package com.kiwobollae.api.ai.analysis.dto;

import java.util.List;

public record JournalImageAnalysisResult(
    ImageQuality imageQuality,
    PlantCondition condition,
    String summary,
    List<String> observations,
    List<String> possibleCauses,
    List<String> recommendedActions,
    List<String> additionalChecks) {

  public enum ImageQuality {
    CLEAR,
    LIMITED,
    UNUSABLE
  }

  public enum PlantCondition {
    HEALTHY,
    NEEDS_ATTENTION,
    URGENT_CHECK,
    UNKNOWN
  }
}
