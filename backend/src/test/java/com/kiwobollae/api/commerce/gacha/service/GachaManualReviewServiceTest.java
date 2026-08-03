package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GachaManualReviewServiceTest {

  @Mock private GachaDrawRepository gachaDrawRepository;

  private GachaManualReviewService service;

  @BeforeEach
  void setUp() {
    service = new GachaManualReviewService(gachaDrawRepository);
  }

  @Test
  void requeuesManualReviewDraw() {
    given(
            gachaDrawRepository.requeueManualReview(
                any(), any(GachaDrawStatus.class), any(GachaDrawStatus.class), any()))
        .willReturn(1);

    var response = service.retry(10L);

    assertThat(response.drawId()).isEqualTo(10L);
    assertThat(response.status()).isEqualTo(GachaDrawStatus.PENDING);
  }

  @Test
  void rejectsRetryWhenDrawDoesNotExist() {
    given(
            gachaDrawRepository.requeueManualReview(
                any(), any(GachaDrawStatus.class), any(GachaDrawStatus.class), any()))
        .willReturn(0);
    given(gachaDrawRepository.existsById(10L)).willReturn(false);

    assertThatThrownBy(() -> service.retry(10L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GACHA_DRAW_NOT_FOUND));
  }

  @Test
  void rejectsRetryUnlessDrawNeedsManualReview() {
    given(
            gachaDrawRepository.requeueManualReview(
                any(), any(GachaDrawStatus.class), any(GachaDrawStatus.class), any()))
        .willReturn(0);
    given(gachaDrawRepository.existsById(10L)).willReturn(true);

    assertThatThrownBy(() -> service.retry(10L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.GACHA_MANUAL_RETRY_INVALID_STATE));
  }
}
