package com.kiwobollae.api.commerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommerceAssetUrlResolver {

  private final String assetBaseUrl;

  public CommerceAssetUrlResolver(@Value("${app.asset.base-url:}") String assetBaseUrl) {
    this.assetBaseUrl = assetBaseUrl == null ? "" : assetBaseUrl.replaceAll("/+$", "");
  }

  public String resolve(String imageKey) {
    if (imageKey == null || imageKey.isBlank()) {
      return null;
    }
    String normalized = imageKey.startsWith("/") ? imageKey.substring(1) : imageKey;
    return assetBaseUrl.isBlank() ? "/" + normalized : assetBaseUrl + "/" + normalized;
  }
}
