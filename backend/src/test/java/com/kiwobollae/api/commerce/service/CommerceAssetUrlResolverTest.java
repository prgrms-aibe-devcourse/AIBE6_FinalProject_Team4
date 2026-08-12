package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommerceAssetUrlResolverTest {

  @Test
  void resolvesRelativeKeyAgainstConfiguredBaseUrl() {
    CommerceAssetUrlResolver resolver =
        new CommerceAssetUrlResolver("https://cdn.example.com/");

    assertThat(resolver.resolve("/cards/1/card.png"))
        .isEqualTo("https://cdn.example.com/cards/1/card.png");
  }

  @Test
  void fallsBackToRootRelativeUrlAndKeepsMissingImageNull() {
    CommerceAssetUrlResolver resolver = new CommerceAssetUrlResolver("");

    assertThat(resolver.resolve("cards/1/card.png")).isEqualTo("/cards/1/card.png");
    assertThat(resolver.resolve(" ")).isNull();
  }
}
