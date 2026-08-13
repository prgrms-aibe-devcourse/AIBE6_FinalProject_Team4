package com.kiwobollae.api.journal.dto.response;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.service.GachaRewardReservation;

public record GachaRewardResponse(boolean granted, Long drawId, GachaDrawStatus status) {
  public static GachaRewardResponse from(
      GachaRewardReservation reservation, boolean dailyRewardGranted) {
    return new GachaRewardResponse(
        dailyRewardGranted && reservation.drawId() != null, reservation.drawId(), reservation.status());
  }
}
