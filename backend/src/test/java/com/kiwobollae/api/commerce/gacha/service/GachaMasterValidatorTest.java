package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GachaMasterValidatorTest {

  private final GachaMasterValidator validator = new GachaMasterValidator();

  @Test
  void acceptsTheFixedSeasonOneDistribution() {
    assertThatCode(() -> validator.validate(validCards())).doesNotThrowAnyException();
  }

  @Test
  void rejectsAnInvalidTotalWeight() {
    List<TradingCard> cards = validCards();
    cards.set(0, card(TradingCardRarity.COMMON, 97_999));

    assertThatThrownBy(() -> validator.validate(cards)).isInstanceOf(BusinessException.class);
  }

  private List<TradingCard> validCards() {
    List<TradingCard> cards = new ArrayList<>();
    add(cards, TradingCardRarity.COMMON, 15, 98_000);
    add(cards, TradingCardRarity.RARE, 14, 30_000);
    add(cards, TradingCardRarity.SUPER_RARE, 8, 23_625);
    add(cards, TradingCardRarity.HYPER_RARE, 3, 6_993);
    add(cards, TradingCardRarity.GOLDEN_RARE, 3, 7);
    return cards;
  }

  private void add(List<TradingCard> cards, TradingCardRarity rarity, int count, int weight) {
    for (int index = 0; index < count; index++) {
      cards.add(card(rarity, weight));
    }
  }

  private TradingCard card(TradingCardRarity rarity, int weight) {
    return TradingCard.builder()
        .code(rarity + "-" + Math.random())
        .name(rarity.name())
        .rarity(rarity)
        .drawWeight(weight)
        .displayOrder(1)
        .build();
  }
}
