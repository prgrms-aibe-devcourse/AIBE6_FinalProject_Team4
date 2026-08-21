package com.kiwobollae.api.ai.knowledge;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 자유 입력 종명을 캐시와 공식 문서 검색이 공유하는 한 규칙으로 정규화한다. */
@Component
public class PlantSpeciesNameNormalizer {

  public static final int MAX_SPECIES_NAME_LENGTH = 100;

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  public NormalizedSpeciesName normalize(String rawName) {
    if (rawName == null || rawName.isBlank()) {
      throw new IllegalArgumentException("식물 종 이름이 필요합니다.");
    }

    String displayName =
        WHITESPACE
            .matcher(Normalizer.normalize(rawName, Normalizer.Form.NFKC).strip())
            .replaceAll(" ");
    if (displayName.isBlank() || displayName.length() > MAX_SPECIES_NAME_LENGTH) {
      throw new IllegalArgumentException("식물 종 이름이 너무 깁니다.");
    }

    String cacheName = WHITESPACE.matcher(displayName).replaceAll("");
    return new NormalizedSpeciesName(displayName, cacheName, cacheName.toLowerCase(Locale.ROOT));
  }

  public record NormalizedSpeciesName(String displayName, String cacheName, String lookupKey) {}
}
