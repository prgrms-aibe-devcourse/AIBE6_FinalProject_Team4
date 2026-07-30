package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.service.GachaRewardReservation;

public record GachaRewardResponse(boolean granted, Long drawId, GachaDrawStatus status) {
  public static GachaRewardResponse from(GachaRewardReservation reservation) {
    return new GachaRewardResponse(
        reservation.granted(), reservation.drawId(), reservation.status());
  }
}
