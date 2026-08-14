package com.kiwobollae.api.ai.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JournalImageAnalysisRequest(@NotBlank @Size(max = 64) String imageHash) {}
