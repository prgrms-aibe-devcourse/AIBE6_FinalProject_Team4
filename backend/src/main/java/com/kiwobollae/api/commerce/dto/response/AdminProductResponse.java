package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import java.time.LocalDateTime;

public record AdminProductResponse(
    Long id,
    String name,
    ProductCategory category,
    Long pointPrice,
    Integer stock,
    boolean unlimitedStock,
    boolean soldOut,
    String speciesName,
    String description,
    String imageKey,
    String imageUrl,
    ProductStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminProductResponse from(Product product, String imageUrl) {
    boolean unlimitedStock = product.getCategory() == ProductCategory.GACHA_PACK;
    return new AdminProductResponse(
        product.getId(),
        product.getName(),
        product.getCategory(),
        product.getPointPrice(),
        product.getStock(),
        unlimitedStock,
        !unlimitedStock && product.getStock() <= 0,
        product.getSpeciesName(),
        product.getDescription(),
        product.getImageUrl(),
        imageUrl,
        product.getStatus(),
        product.getCreatedAt(),
        product.getUpdatedAt());
  }
}
