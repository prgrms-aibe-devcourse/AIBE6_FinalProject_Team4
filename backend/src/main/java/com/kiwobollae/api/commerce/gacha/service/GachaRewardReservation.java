package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;

public record GachaRewardReservation(boolean granted, Long drawId, GachaDrawStatus status) {
  public static GachaRewardReservation none() {
    return new GachaRewardReservation(false, null, null);
  }
}
