package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GachaDrawEngine {

  public static final int TOTAL_WEIGHT = 2_100_000;

  private final GachaRandomSource randomSource;

  public GachaDrawEngine(GachaRandomSource randomSource) {
    this.randomSource = randomSource;
  }

  public RolledGrade rollGrade() {
    int rollValue = randomSource.nextInt(TOTAL_WEIGHT);
    return new RolledGrade(rollValue, rarityFor(rollValue));
  }

  public TradingCard chooseCard(List<TradingCard> candidates) {
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("Card candidates must not be empty.");
    }
    return candidates.get(randomSource.nextInt(candidates.size()));
  }

  public TradingCardRarity rarityFor(int rollValue) {
    if (rollValue < 0 || rollValue >= TOTAL_WEIGHT) {
      throw new IllegalArgumentException("rollValue must be between 0 and 2099999.");
    }
    if (rollValue < 1_470_000) {
      return TradingCardRarity.COMMON;
    }
    if (rollValue < 1_890_000) {
      return TradingCardRarity.RARE;
    }
    if (rollValue < 2_079_000) {
      return TradingCardRarity.SUPER_RARE;
    }
    if (rollValue < 2_099_979) {
      return TradingCardRarity.HYPER_RARE;
    }
    return TradingCardRarity.GOLDEN_RARE;
  }

  public record RolledGrade(int rollValue, TradingCardRarity rarity) {}
}
