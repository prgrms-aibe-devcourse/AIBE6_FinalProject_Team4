package com.kiwobollae.api.global.asset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AssetUrlResolver {

  private final String baseUrl;

  public AssetUrlResolver(@Value("${app.asset.base-url:}") String baseUrl) {
    this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
  }

  public String resolve(String imageKeyOrUrl) {
    if (imageKeyOrUrl == null || imageKeyOrUrl.isBlank()) {
      return null;
    }
    String value = imageKeyOrUrl.trim();
    if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) {
      return value;
    }
    String key = value.startsWith("/") ? value.substring(1) : value;
    return baseUrl.isBlank() ? "/" + key : baseUrl + "/" + key;
  }
}
