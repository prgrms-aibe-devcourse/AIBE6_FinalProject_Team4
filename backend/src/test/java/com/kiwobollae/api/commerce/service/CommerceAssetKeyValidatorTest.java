package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

class CommerceAssetKeyValidatorTest {

  private final CommerceAssetKeyValidator validator = new CommerceAssetKeyValidator();

  @Test
  void acceptsMatchingProductS3Key() {
    String key = "products/11/f7573887-a33e-5690-b058-f32f7aa2a326.png";

    assertThat(validator.validate(key, "products", 11L)).isEqualTo(key);
  }

  @Test
  void rejectsWrongPrefixOrResourceId() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "coupons/11/f7573887-a33e-5690-b058-f32f7aa2a326.png",
                    "products",
                    11L))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                validator.validate(
                    "products/12/f7573887-a33e-5690-b058-f32f7aa2a326.png",
                    "products",
                    11L))
        .isInstanceOf(BusinessException.class);
  }
}
