package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.point.service.WalletService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GachaFailureServiceTest {

  private GachaDrawRepository drawRepository;
  private WalletService walletService;
  private GachaFailureService service;

  @BeforeEach
  void setUp() {
    drawRepository = mock(GachaDrawRepository.class);
    walletService = mock(WalletService.class);
    service = new GachaFailureService(drawRepository, walletService);
  }

  @Test
  void purchaseMovesToManualReviewAfterFourthAutomaticFailure() {
    GachaDraw draw = purchaseDraw(3);
    given(drawRepository.findByIdForUpdate(10L)).willReturn(Optional.of(draw));

    service.recordFailure(10L, new IllegalStateException("failed"));

    assertThat(draw.getStatus()).isEqualTo(GachaDrawStatus.MANUAL_REVIEW);
    assertThat(draw.getAttemptCount()).isEqualTo(4);
    verify(walletService, never()).restoreGachaPurchasePoints(7L, 501L);
  }

  @Test
  void refundsPurchaseWhenManualRetryFails() {
    GachaDraw draw = purchaseDraw(4);
    given(drawRepository.findByIdForUpdate(10L)).willReturn(Optional.of(draw));

    service.recordFailure(10L, new IllegalStateException("failed again"));

    verify(walletService).restoreGachaPurchasePoints(7L, 501L);
    assertThat(draw.getStatus()).isEqualTo(GachaDrawStatus.REFUNDED);
    assertThat(draw.getLastErrorCode()).isEqualTo("IllegalStateException");
  }

  @Test
  void freeJournalRewardIsNotRefundedAfterManualRetryFailure() {
    User user = mock(User.class);
    GachaDraw draw =
        GachaDraw.builder()
            .user(user)
            .sourceType(GachaSourceType.LOG_REWARD)
            .sourceId(601L)
            .status(GachaDrawStatus.PENDING)
            .attemptCount(4)
            .build();
    given(drawRepository.findByIdForUpdate(10L)).willReturn(Optional.of(draw));

    service.recordFailure(10L, new IllegalStateException("failed again"));

    assertThat(draw.getStatus()).isEqualTo(GachaDrawStatus.MANUAL_REVIEW);
    verify(walletService, never()).restoreGachaPurchasePoints(7L, 601L);
  }

  private GachaDraw purchaseDraw(int attemptCount) {
    User user = mock(User.class);
    given(user.getId()).willReturn(7L);
    return GachaDraw.builder()
        .user(user)
        .sourceType(GachaSourceType.PURCHASE)
        .sourceId(501L)
        .status(GachaDrawStatus.PENDING)
        .attemptCount(attemptCount)
        .build();
  }
}
