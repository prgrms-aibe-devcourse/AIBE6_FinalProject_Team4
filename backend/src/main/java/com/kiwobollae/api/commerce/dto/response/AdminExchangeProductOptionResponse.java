package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.ExchangeProduct;

public record AdminExchangeProductOptionResponse(Long id, String name, Integer stock) {

  public static AdminExchangeProductOptionResponse from(ExchangeProduct product) {
    return new AdminExchangeProductOptionResponse(
        product.getId(), product.getName(), product.getStock());
  }
}
