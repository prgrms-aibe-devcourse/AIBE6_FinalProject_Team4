package com.kiwobollae.api.global.asset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssetUrlResolverTest {

  private final AssetUrlResolver resolver =
      new AssetUrlResolver("https://bucket.s3.ap-northeast-2.amazonaws.com/");

  @Test
  void resolvesRelativeObjectKeyAgainstConfiguredBaseUrl() {
    assertThat(resolver.resolve("products/1/product.png"))
        .isEqualTo("https://bucket.s3.ap-northeast-2.amazonaws.com/products/1/product.png");
    assertThat(resolver.resolve("/coupons/1/coupon.png"))
        .isEqualTo("https://bucket.s3.ap-northeast-2.amazonaws.com/coupons/1/coupon.png");
  }

  @Test
  void preservesAbsoluteUrlForAdminAndLegacyData() {
    assertThat(resolver.resolve("https://cdn.example.com/product.png"))
        .isEqualTo("https://cdn.example.com/product.png");
  }

  @Test
  void fallsBackToRootRelativeUrlWithoutBaseUrl() {
    assertThat(new AssetUrlResolver("").resolve("products/1/product.png"))
        .isEqualTo("/products/1/product.png");
  }

  @Test
  void returnsNullForMissingValue() {
    assertThat(resolver.resolve(null)).isNull();
    assertThat(resolver.resolve("  ")).isNull();
  }
}
