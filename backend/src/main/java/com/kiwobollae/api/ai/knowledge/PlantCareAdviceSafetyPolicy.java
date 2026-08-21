package com.kiwobollae.api.ai.knowledge;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.Collection;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 공식 근거가 없는 응답에서 위험한 농약·비료 처방을 서버가 한 번 더 차단한다. */
@Component
public class PlantCareAdviceSafetyPolicy {

  private static final Pattern CHEMICAL_PRESCRIPTION =
      Pattern.compile(
          "(?i)(농약|살충제|살균제|제초제|비료|영양제|pesticide|fertilizer)"
              + ".{0,24}(사용하세요|사용합니다|뿌리세요|뿌립니다|살포하세요|살포합니다|"
              + "투입하세요|투입합니다|주세요|줍니다|희석하세요|희석합니다|apply|spray|use)");

  private static final Pattern EXACT_AMOUNT_PRESCRIPTION =
      Pattern.compile(
          "(?i)(?:\\d+(?:[.,]\\d+)?\\s*(?:ml|l|cc|mg|g|kg|배|스푼|큰술|작은술))"
              + ".{0,24}(?:주세요|줍니다|주입하세요|주입합니다|뿌리세요|뿌립니다|살포하세요|"
              + "살포합니다|투입하세요|투입합니다|희석하세요|희석합니다|사용하세요|사용합니다|apply|spray|use)");

  private static final Pattern EXACT_CADENCE_PRESCRIPTION =
      Pattern.compile(
          "(?i)(?:매\\s*)?(?:\\d+\\s*(?:일|주|개월)\\s*(?:마다|간격)|하루\\s*\\d+\\s*회|주\\s*\\d+\\s*회)"
              + ".{0,24}(?:주세요|줍니다|뿌리세요|뿌립니다|살포하세요|살포합니다|투입하세요|"
              + "투입합니다|희석하세요|희석합니다|사용하세요|사용합니다|apply|spray|use)");

  public void validate(PlantCareEvidenceStatus evidenceStatus, Collection<String> generatedTexts) {
    if (evidenceStatus != PlantCareEvidenceStatus.GENERAL_FALLBACK) {
      return;
    }
    if (generatedTexts == null
        || generatedTexts.stream()
            .filter(java.util.Objects::nonNull)
            .anyMatch(
                text ->
                    CHEMICAL_PRESCRIPTION.matcher(text).find()
                        || EXACT_AMOUNT_PRESCRIPTION.matcher(text).find()
                        || EXACT_CADENCE_PRESCRIPTION.matcher(text).find())) {
      throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
    }
  }
}
