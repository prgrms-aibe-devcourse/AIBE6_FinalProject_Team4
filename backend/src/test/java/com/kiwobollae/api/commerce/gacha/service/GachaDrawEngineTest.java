package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class GachaDrawEngineTest {

  @Test
  void mapsEveryRarityBoundary() {
    GachaDrawEngine engine = new GachaDrawEngine(bound -> 0);

    assertThat(engine.rarityFor(0)).isEqualTo(TradingCardRarity.COMMON);
    assertThat(engine.rarityFor(1_469_999)).isEqualTo(TradingCardRarity.COMMON);
    assertThat(engine.rarityFor(1_470_000)).isEqualTo(TradingCardRarity.RARE);
    assertThat(engine.rarityFor(1_889_999)).isEqualTo(TradingCardRarity.RARE);
    assertThat(engine.rarityFor(1_890_000)).isEqualTo(TradingCardRarity.SUPER_RARE);
    assertThat(engine.rarityFor(2_078_999)).isEqualTo(TradingCardRarity.SUPER_RARE);
    assertThat(engine.rarityFor(2_079_000)).isEqualTo(TradingCardRarity.HYPER_RARE);
    assertThat(engine.rarityFor(2_099_978)).isEqualTo(TradingCardRarity.HYPER_RARE);
    assertThat(engine.rarityFor(2_099_979)).isEqualTo(TradingCardRarity.GOLDEN_RARE);
    assertThat(engine.rarityFor(2_099_999)).isEqualTo(TradingCardRarity.GOLDEN_RARE);
  }

  @Test
  void rejectsRollOutsideFixedRange() {
    GachaDrawEngine engine = new GachaDrawEngine(bound -> 0);

    assertThatThrownBy(() -> engine.rarityFor(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> engine.rarityFor(GachaDrawEngine.TOTAL_WEIGHT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void recordsTheRandomValueUsedForGrade() {
    Queue<Integer> values = new ArrayDeque<>();
    values.add(2_099_979);
    GachaDrawEngine engine = new GachaDrawEngine(bound -> values.remove());

    GachaDrawEngine.RolledGrade result = engine.rollGrade();

    assertThat(result.rollValue()).isEqualTo(2_099_979);
    assertThat(result.rarity()).isEqualTo(TradingCardRarity.GOLDEN_RARE);
  }
}
