package com.kiwobollae.api.commerce.dto.request;

import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminProductRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull ProductCategory category,
    @NotNull @PositiveOrZero Long pointPrice,
    @NotNull @PositiveOrZero Integer stock,
    Long plantId,
    @Size(max = 2000) String description,
    @Size(max = 500) String imageUrl) {}
