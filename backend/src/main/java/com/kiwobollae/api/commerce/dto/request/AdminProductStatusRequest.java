package com.kiwobollae.api.commerce.dto.request;

import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record AdminProductStatusRequest(@NotNull ProductStatus status) {}
