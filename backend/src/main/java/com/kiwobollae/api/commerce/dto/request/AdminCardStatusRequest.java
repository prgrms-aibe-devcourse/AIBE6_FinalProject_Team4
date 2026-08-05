package com.kiwobollae.api.commerce.dto.request;

import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import jakarta.validation.constraints.NotNull;

public record AdminCardStatusRequest(@NotNull ActiveStatus status) {}
