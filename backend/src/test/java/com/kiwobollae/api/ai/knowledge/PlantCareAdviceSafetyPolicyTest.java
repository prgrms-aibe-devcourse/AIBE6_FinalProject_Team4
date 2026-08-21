package com.kiwobollae.api.ai.knowledge;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantCareAdviceSafetyPolicyTest {

  private final PlantCareAdviceSafetyPolicy policy = new PlantCareAdviceSafetyPolicy();

  @Test
  void rejectsChemicalPrescriptionWithoutVerifiedEvidence() {
    assertThatThrownBy(
            () ->
                policy.validate(PlantCareEvidenceStatus.GENERAL_FALLBACK, List.of("살충제를 잎에 뿌리세요.")))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsExactAmountOrCadenceWithoutVerifiedEvidence() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    PlantCareEvidenceStatus.GENERAL_FALLBACK, List.of("물 500ml를 화분에 주세요.")))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                policy.validate(
                    PlantCareEvidenceStatus.GENERAL_FALLBACK, List.of("3일마다 영양제를 사용하세요.")))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void allowsConservativeFallbackAndDoesNotRestrictVerifiedEvidence() {
    assertThatCode(
            () ->
                policy.validate(
                    PlantCareEvidenceStatus.GENERAL_FALLBACK,
                    List.of("잎과 흙을 관찰하고 제품 표시사항을 확인해 주세요.")))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                policy.validate(
                    PlantCareEvidenceStatus.VERIFIED, List.of("공식 근거에 따라 물 500ml를 주세요.")))
        .doesNotThrowAnyException();
  }
}
