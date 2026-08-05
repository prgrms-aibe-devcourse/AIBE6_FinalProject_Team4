package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;

public final class GachaShardPolicy {

  private GachaShardPolicy() {}

  public static int shardPerCard(TradingCardRarity rarity) {
    return switch (rarity) {
      case COMMON -> 1;
      case RARE -> 3;
      case SUPER_RARE -> 10;
      case HYPER_RARE, GOLDEN_RARE -> 0;
    };
  }

  public static int dismantleableCount(TradingCardRarity rarity, int ownedCount) {
    return shardPerCard(rarity) == 0 ? 0 : Math.max(0, ownedCount - 1);
  }
}
