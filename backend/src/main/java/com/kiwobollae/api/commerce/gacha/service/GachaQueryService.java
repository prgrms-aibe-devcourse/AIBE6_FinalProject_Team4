package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.commerce.gacha.dto.GachaCardResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaCollectionResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaDrawDetailResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaDrawItemResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaDrawPageResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaDrawSummaryResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaRateResponse;
import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCollection;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawItemRepository;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.commerce.service.CommerceAssetUrlResolver;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GachaQueryService {

  private static final List<Integer> MILESTONES = List.of(1, 3, 5, 10, 25, 50);

  private final TradingCardRepository tradingCardRepository;
  private final UserCardCollectionRepository collectionRepository;
  private final GachaDrawRepository gachaDrawRepository;
  private final GachaDrawItemRepository gachaDrawItemRepository;
  private final CommerceAssetUrlResolver assetUrlResolver;

  public List<GachaCardResponse> getCatalog(TradingCardRarity rarity) {
    List<TradingCard> cards =
        rarity == null
            ? tradingCardRepository.findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus.ACTIVE)
            : tradingCardRepository.findAllByStatusAndRarityOrderByDisplayOrderAsc(
                TradingCardStatus.ACTIVE, rarity);
    // 공개 카탈로그는 뽑기 전 원본 일러스트를 노출하지 않는다(획득한 카드만 해금).
    return cards.stream().map(card -> GachaCardResponse.from(card, null)).toList();
  }

  public List<GachaCollectionResponse> getCollection(Long userId) {
    requireUser(userId);
    List<TradingCard> active =
        tradingCardRepository.findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus.ACTIVE);
    Map<Long, UserCardCollection> collections =
        collectionRepository.findAllByUser_Id(userId).stream()
            .collect(
                Collectors.toMap(collection -> collection.getCard().getId(), Function.identity()));
    Map<Long, TradingCard> cards = new LinkedHashMap<>();
    active.forEach(card -> cards.put(card.getId(), card));
    collections.values().stream()
        .filter(
            collection ->
                collection.getOwnedCount() > 0 || collection.getGoldenGachaAcquiredAt() != null)
        .map(UserCardCollection::getCard)
        .forEach(card -> cards.putIfAbsent(card.getId(), card));
    return cards.values().stream()
        .map(
            card -> {
              UserCardCollection collection = collections.get(card.getId());
              String unlockedImageUrl =
                  collection == null ? null : assetUrlResolver.resolve(card.getImageKey());
              return GachaCollectionResponse.from(card, collection, unlockedImageUrl);
            })
        .toList();
  }

  public GachaRateResponse getRates() {
    List<GachaRateResponse.RarityRate> rates = new ArrayList<>();
    rates.add(rate(TradingCardRarity.COMMON, 1_470_000));
    rates.add(rate(TradingCardRarity.RARE, 420_000));
    rates.add(rate(TradingCardRarity.SUPER_RARE, 189_000));
    rates.add(rate(TradingCardRarity.HYPER_RARE, 20_979));
    rates.add(rate(TradingCardRarity.GOLDEN_RARE, 21));
    return new GachaRateResponse(
        1,
        5,
        GachaDrawEngine.TOTAL_WEIGHT,
        rates,
        List.of(
            "천장과 등급 보장은 없습니다.",
            "골든 카드는 종류별로 가챠에서 평생 1회만 획득할 수 있습니다.",
            "골든 3종을 모두 가챠로 획득하면 골든 구간은 하이퍼 레어로 대체됩니다."));
  }

  public GachaDrawPageResponse getHistory(Long userId, Boolean viewed, Pageable pageable) {
    requireUser(userId);
    Page<GachaDrawSummaryResponse> result =
        gachaDrawRepository
            .findHistory(userId, viewed, GachaDrawStatus.REFUNDED, pageable)
            .map(GachaDrawSummaryResponse::from);
    return GachaDrawPageResponse.from(result);
  }

  public GachaDrawDetailResponse getDraw(Long userId, Long drawId) {
    requireUser(userId);
    GachaDraw draw = findOwnedDraw(userId, drawId);
    if (draw.getStatus() == GachaDrawStatus.MANUAL_REVIEW) {
      throw new BusinessException(ErrorCode.GACHA_REWARD_MANUAL_REVIEW);
    }
    if (draw.getStatus() != GachaDrawStatus.COMPLETED) {
      return GachaDrawDetailResponse.from(draw, List.of());
    }
    List<GachaDrawItemResponse> items =
        gachaDrawItemRepository.findAllByGachaDraw_IdOrderByDrawSeqAsc(drawId).stream()
            .map(
                item ->
                    GachaDrawItemResponse.from(
                        item,
                        assetUrlResolver.resolve(item.getCard().getImageKey()),
                        nextMilestone(item.getOwnedCountAfter())))
            .toList();
    return GachaDrawDetailResponse.from(draw, items);
  }

  @Transactional
  public GachaDrawDetailResponse markViewed(Long userId, Long drawId) {
    requireUser(userId);
    GachaDraw draw = findOwnedDraw(userId, drawId);
    if (draw.getStatus() != GachaDrawStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.GACHA_DRAW_NOT_COMPLETED);
    }
    gachaDrawRepository.markViewedIfAbsent(
        drawId, userId, GachaDrawStatus.COMPLETED, LocalDateTime.now(KST));
    return getDraw(userId, drawId);
  }

  private GachaDraw findOwnedDraw(Long userId, Long drawId) {
    return gachaDrawRepository
        .findByIdAndUser_Id(drawId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_DRAW_NOT_FOUND));
  }

  private GachaRateResponse.RarityRate rate(TradingCardRarity rarity, int weight) {
    BigDecimal percent =
        BigDecimal.valueOf(weight)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(GachaDrawEngine.TOTAL_WEIGHT));
    return new GachaRateResponse.RarityRate(rarity, weight, percent);
  }

  private Integer nextMilestone(int ownedCount) {
    return MILESTONES.stream().filter(value -> value > ownedCount).findFirst().orElse(null);
  }

  private void requireUser(Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
  }
}
