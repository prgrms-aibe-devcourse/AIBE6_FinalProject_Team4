package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.GachaDrawItem;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCollection;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GoldenOriginType;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawItemRepository;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GachaDrawTransactionService {

  private final GachaDrawRepository gachaDrawRepository;
  private final GachaDrawItemRepository gachaDrawItemRepository;
  private final TradingCardRepository tradingCardRepository;
  private final UserCardCollectionRepository collectionRepository;
  private final GoldenCardInstanceRepository goldenInstanceRepository;
  private final GachaDrawEngine drawEngine;
  private final GachaMasterValidator masterValidator;
  private final GachaCollectionAcquisitionService collectionAcquisitionService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(Long drawId) {
    LocalDateTime now = LocalDateTime.now(KST);
    int claimed =
        gachaDrawRepository.claimForProcessing(
            drawId,
            now,
            GachaDrawStatus.PENDING,
            GachaDrawStatus.RETRYABLE_FAILED,
            GachaDrawStatus.PROCESSING);
    if (claimed == 0) {
      return;
    }

    GachaDraw draw =
        gachaDrawRepository
            .findByIdForUpdate(drawId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_DRAW_NOT_FOUND));
    List<TradingCard> activeCards =
        tradingCardRepository.findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus.ACTIVE);
    masterValidator.validate(activeCards);
    Map<TradingCardRarity, List<TradingCard>> cardsByRarity =
        activeCards.stream()
            .collect(
                Collectors.groupingBy(
                    TradingCard::getRarity,
                    () -> new EnumMap<>(TradingCardRarity.class),
                    Collectors.toList()));

    for (int sequence = 1; sequence <= draw.getDrawCount(); sequence++) {
      drawOne(draw, sequence, cardsByRarity, now);
    }
    draw.complete(now);
  }

  private void drawOne(
      GachaDraw draw,
      int sequence,
      Map<TradingCardRarity, List<TradingCard>> cardsByRarity,
      LocalDateTime now) {
    GachaDrawEngine.RolledGrade rolled = drawEngine.rollGrade();
    TradingCardRarity finalRarity = rolled.rarity();
    TradingCard card;
    GoldenCardInstance goldenInstance = null;
    int ownedCountAfter;

    if (rolled.rarity() == TradingCardRarity.GOLDEN_RARE) {
      GoldenAcquisition golden = acquireGolden(draw.getUser(), cardsByRarity, now);
      if (golden == null) {
        finalRarity = TradingCardRarity.HYPER_RARE;
        card = drawEngine.chooseCard(requiredCandidates(cardsByRarity, finalRarity));
        ownedCountAfter =
            collectionAcquisitionService.acquireNormal(draw.getUser().getId(), card.getId(), now);
      } else {
        card = golden.card();
        goldenInstance = golden.instance();
        ownedCountAfter = golden.ownedCountAfter();
      }
    } else {
      card = drawEngine.chooseCard(requiredCandidates(cardsByRarity, finalRarity));
      ownedCountAfter =
          collectionAcquisitionService.acquireNormal(draw.getUser().getId(), card.getId(), now);
    }

    gachaDrawItemRepository.save(
        GachaDrawItem.builder()
            .gachaDraw(draw)
            .drawSeq(sequence)
            .card(card)
            .rollValue(rolled.rollValue())
            .rolledRarity(rolled.rarity())
            .finalRarity(finalRarity)
            .ownedCountAfter(ownedCountAfter)
            .goldenInstance(goldenInstance)
            .createdAt(now)
            .build());
  }

  private GoldenAcquisition acquireGolden(
      User user, Map<TradingCardRarity, List<TradingCard>> cardsByRarity, LocalDateTime now) {
    List<TradingCard> goldenCards =
        requiredCandidates(cardsByRarity, TradingCardRarity.GOLDEN_RARE);
    Set<Long> acquired =
        new HashSet<>(
            collectionRepository.findGachaAcquiredCardIds(
                user.getId(), TradingCardRarity.GOLDEN_RARE));

    while (acquired.size() < goldenCards.size()) {
      List<TradingCard> candidates =
          goldenCards.stream().filter(card -> !acquired.contains(card.getId())).toList();
      TradingCard selected = drawEngine.chooseCard(candidates);
      collectionRepository.ensureCollectionRow(user.getId(), selected.getId(), now);
      UserCardCollection collection =
          collectionRepository
              .findForUpdate(user.getId(), selected.getId())
              .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_MASTER_DATA_INVALID));
      int ownedCountAfter = collection.acquireGolden(now);
      if (ownedCountAfter == 0) {
        acquired.add(selected.getId());
        continue;
      }

      TradingCard lockedCard =
          tradingCardRepository
              .findByIdForUpdate(selected.getId())
              .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_MASTER_DATA_INVALID));
      long originRank = goldenInstanceRepository.findMaxOriginRank(selected.getId()) + 1;
      GoldenCardInstance instance =
          goldenInstanceRepository.save(
              GoldenCardInstance.builder()
                  .card(lockedCard)
                  .ownerUser(user)
                  .originUser(user)
                  .originType(GoldenOriginType.GACHA)
                  .goldenOriginRank(originRank)
                  .originAcquiredAt(now)
                  .currentOwnerSince(now)
                  .createdAt(now)
                  .build());
      return new GoldenAcquisition(selected, instance, ownedCountAfter);
    }
    return null;
  }

  private List<TradingCard> requiredCandidates(
      Map<TradingCardRarity, List<TradingCard>> cardsByRarity, TradingCardRarity rarity) {
    List<TradingCard> candidates = cardsByRarity.getOrDefault(rarity, List.of());
    if (candidates.isEmpty()) {
      throw new BusinessException(ErrorCode.GACHA_MASTER_DATA_INVALID);
    }
    return candidates;
  }

  private record GoldenAcquisition(
      TradingCard card, GoldenCardInstance instance, int ownedCountAfter) {}
}
