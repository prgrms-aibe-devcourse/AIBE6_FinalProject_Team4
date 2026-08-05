package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CommerceAssetKeyValidator {

  private static final Pattern ASSET_KEY =
      Pattern.compile(
          "^(products|coupons)/(\\d+)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})\\.(png|jpe?g|webp)$");

  public String validate(String value, String expectedPrefix, Long expectedResourceId) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String key = value.trim();
    Matcher matcher = ASSET_KEY.matcher(key);
    if (!matcher.matches() || !matcher.group(1).equals(expectedPrefix)) {
      throw invalid(
          "imageUrl",
          value,
          expectedPrefix + "/{id}/{uuid}.png 형식의 S3 상대 경로를 입력해야 합니다.");
    }
    if (expectedResourceId != null && !matcher.group(2).equals(String.valueOf(expectedResourceId))) {
      throw invalid("imageUrl", value, "S3 경로의 id가 대상 데이터 id와 일치해야 합니다.");
    }
    return key;
  }

  private BusinessException invalid(String field, Object value, String reason) {
    return new BusinessException(
        ErrorCode.COMMON_VALIDATION_FAILED,
        Map.of("field", field, "rejectedValue", String.valueOf(value), "reason", reason));
  }
}
