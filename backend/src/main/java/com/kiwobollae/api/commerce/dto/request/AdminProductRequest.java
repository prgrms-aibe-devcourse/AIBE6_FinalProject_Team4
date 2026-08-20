package com.kiwobollae.api.commerce.dto.request;

import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminProductRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull ProductCategory category,
    @NotNull @PositiveOrZero Long pointPrice,
    @NotNull @PositiveOrZero Integer stock,
    @Size(max = 100) @Pattern(regexp = "[가-힣a-zA-Z ]*", message = "종 이름에는 한글/영문/공백만 사용할 수 있습니다.")
        String speciesName,
    @Size(max = 2000) String description,
    @Size(max = 500) String imageUrl) {}
