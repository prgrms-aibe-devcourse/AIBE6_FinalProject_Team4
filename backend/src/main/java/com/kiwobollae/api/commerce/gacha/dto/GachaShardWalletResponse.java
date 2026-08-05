package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;

public record GachaShardWalletResponse(long balance, long lifetimeEarned, long lifetimeSpent) {
  public static GachaShardWalletResponse empty() {
    return new GachaShardWalletResponse(0L, 0L, 0L);
  }

  public static GachaShardWalletResponse from(UserCardShardWallet wallet) {
    return new GachaShardWalletResponse(
        wallet.getBalance(), wallet.getLifetimeEarned(), wallet.getLifetimeSpent());
  }
}
