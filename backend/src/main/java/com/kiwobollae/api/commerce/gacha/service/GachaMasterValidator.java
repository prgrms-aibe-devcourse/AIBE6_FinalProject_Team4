package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GachaMasterValidator {

  private static final Map<TradingCardRarity, Integer> EXPECTED_COUNTS =
      Map.of(
          TradingCardRarity.COMMON, 15,
          TradingCardRarity.RARE, 14,
          TradingCardRarity.SUPER_RARE, 8,
          TradingCardRarity.HYPER_RARE, 3,
          TradingCardRarity.GOLDEN_RARE, 3);

  public void validate(List<TradingCard> cards) {
    Map<TradingCardRarity, Integer> counts = new EnumMap<>(TradingCardRarity.class);
    int totalWeight = 0;
    for (TradingCard card : cards) {
      counts.merge(card.getRarity(), 1, Integer::sum);
      totalWeight += card.getDrawWeight();
    }
    if (cards.size() != 43
        || totalWeight != GachaDrawEngine.TOTAL_WEIGHT
        || !EXPECTED_COUNTS.equals(counts)) {
      throw new BusinessException(ErrorCode.GACHA_MASTER_DATA_INVALID);
    }
  }
}
