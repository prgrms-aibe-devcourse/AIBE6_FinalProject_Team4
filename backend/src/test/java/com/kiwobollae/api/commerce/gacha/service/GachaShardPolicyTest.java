package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import org.junit.jupiter.api.Test;

class GachaShardPolicyTest {

  @Test
  void calculatesShardValuesByDismantleableRarity() {
    assertThat(GachaShardPolicy.shardPerCard(TradingCardRarity.COMMON)).isEqualTo(1);
    assertThat(GachaShardPolicy.shardPerCard(TradingCardRarity.RARE)).isEqualTo(3);
    assertThat(GachaShardPolicy.shardPerCard(TradingCardRarity.SUPER_RARE)).isEqualTo(10);
  }

  @Test
  void blocksPremiumRaritiesAndAlwaysKeepsOneCard() {
    assertThat(GachaShardPolicy.shardPerCard(TradingCardRarity.HYPER_RARE)).isZero();
    assertThat(GachaShardPolicy.shardPerCard(TradingCardRarity.GOLDEN_RARE)).isZero();
    assertThat(GachaShardPolicy.dismantleableCount(TradingCardRarity.COMMON, 1)).isZero();
    assertThat(GachaShardPolicy.dismantleableCount(TradingCardRarity.COMMON, 5)).isEqualTo(4);
  }
}
