package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaTestCardGrantRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaTestCardGrantResponse;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.GoldenOriginType;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalGachaTestCardGrantService {

  private final UserRepository userRepository;
  private final TradingCardRepository tradingCardRepository;
  private final GoldenCardInstanceRepository goldenCardInstanceRepository;
  private final GachaCollectionAcquisitionService collectionAcquisitionService;
  private final GachaRandomSource randomSource;
  private final Clock clock;

  @Transactional
  public GachaTestCardGrantResponse grant(Long userId, GachaTestCardGrantRequest request) {
    TradingCardRarity rarity = request.rarity();
    if (rarity != TradingCardRarity.HYPER_RARE && rarity != TradingCardRarity.GOLDEN_RARE) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
    List<TradingCard> candidates =
        tradingCardRepository.findAllByStatusAndRarityOrderByDisplayOrderAsc(
            TradingCardStatus.ACTIVE, rarity);
    if (candidates.isEmpty()) {
      throw new BusinessException(ErrorCode.GACHA_MASTER_DATA_INVALID);
    }

    TradingCard selected = candidates.get(randomSource.nextInt(candidates.size()));
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    int ownedCountAfter = 0;
    for (int count = 0; count < request.quantity(); count++) {
      ownedCountAfter = collectionAcquisitionService.acquireNormal(userId, selected.getId(), now);
      if (rarity == TradingCardRarity.GOLDEN_RARE) {
        createGoldenInstance(user, selected, now);
      }
    }

    return new GachaTestCardGrantResponse(
        selected.getId(), selected.getName(), rarity, request.quantity(), ownedCountAfter);
  }

  private void createGoldenInstance(User user, TradingCard card, LocalDateTime now) {
    goldenCardInstanceRepository.save(
        GoldenCardInstance.builder()
            .card(card)
            .ownerUser(user)
            .originUser(user)
            .originType(GoldenOriginType.ADMIN)
            .goldenOriginRank(null)
            .originAcquiredAt(now)
            .currentOwnerSince(now)
            .createdAt(now)
            .build());
  }
}
