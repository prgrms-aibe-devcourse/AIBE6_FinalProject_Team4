package com.kiwobollae.api.commerce.gacha.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GachaQaDrawRequest(@NotBlank @Size(max = 100) String clientKey) {}
