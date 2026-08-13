package com.kiwobollae.api.commerce.cardmarket.infrastructure;

import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.service.WalletService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CardMarketPointAdapter implements CardMarketPointPort {

  private final WalletService walletService;

  @Override
  public Balance getBalance(Long userId) {
    WalletResponse wallet = walletService.getWallet(userId);
    return new Balance(wallet.paidPoint(), wallet.freePoint());
  }

  @Override
  @Transactional
  public void reserveOffer(Long userId, long amount, Long negotiationId) {
    validateOffer(userId, amount, negotiationId);
    apply(
        userId,
        PointTxType.MARKET_ESCROW,
        Math.negateExact(amount),
        PointRefType.MARKET_OFFER,
        negotiationId);
  }

  @Override
  @Transactional
  public void releaseOffer(Long userId, long amount, Long negotiationId) {
    validateOffer(userId, amount, negotiationId);
    apply(
        userId,
        PointTxType.MARKET_RELEASE,
        amount,
        PointRefType.MARKET_OFFER,
        negotiationId);
  }

  @Override
  @Transactional
  public void releaseOffers(List<OfferRelease> releases) {
    for (PointOperation operation : releaseOperations(releases)) {
      apply(operation);
    }
  }

  @Override
  @Transactional
  public void settleTrade(
      Long buyerUserId,
      Long sellerUserId,
      long buyerCharge,
      long sellerReceived,
      Long tradeId,
      List<OfferRelease> releases) {
    validateTrade(buyerUserId, sellerUserId, buyerCharge, sellerReceived, tradeId);
    Map<Long, List<PointOperation>> operations = new TreeMap<>();
    for (PointOperation release : releaseOperations(releases)) {
      operations.computeIfAbsent(release.userId(), ignored -> new ArrayList<>()).add(release);
    }
    if (buyerCharge > 0) {
      operations
          .computeIfAbsent(buyerUserId, ignored -> new ArrayList<>())
          .add(
              new PointOperation(
                  buyerUserId,
                  PointTxType.MARKET_PURCHASE,
                  Math.negateExact(buyerCharge),
                  PointRefType.MARKET_TRADE,
                  tradeId));
    }
    operations
        .computeIfAbsent(sellerUserId, ignored -> new ArrayList<>())
        .add(
            new PointOperation(
                sellerUserId,
                PointTxType.MARKET_SALE,
                sellerReceived,
                PointRefType.MARKET_TRADE,
                tradeId));
    operations.values().stream().flatMap(List::stream).forEach(this::apply);
  }

  private List<PointOperation> releaseOperations(List<OfferRelease> releases) {
    if (releases == null || releases.isEmpty()) {
      return List.of();
    }
    return releases.stream()
        .peek(release -> validateOffer(release.userId(), release.amount(), release.negotiationId()))
        .sorted(
            Comparator.comparing(OfferRelease::userId)
                .thenComparing(OfferRelease::negotiationId))
        .map(
            release ->
                new PointOperation(
                    release.userId(),
                    PointTxType.MARKET_RELEASE,
                    release.amount(),
                    PointRefType.MARKET_OFFER,
                    release.negotiationId()))
        .toList();
  }

  private void apply(PointOperation operation) {
    apply(
        operation.userId(),
        operation.type(),
        operation.amount(),
        operation.refType(),
        operation.refId());
  }

  private void apply(
      Long userId, PointTxType type, long amount, PointRefType refType, Long refId) {
    walletService.applyDelta(userId, type, CurrencyType.PAID, amount, refType, refId);
  }

  private void validateOffer(Long userId, long amount, Long negotiationId) {
    if (userId == null || userId < 1 || amount < 1 || negotiationId == null || negotiationId < 1) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
  }

  private void validateTrade(
      Long buyerUserId,
      Long sellerUserId,
      long buyerCharge,
      long sellerReceived,
      Long tradeId) {
    if (buyerUserId == null
        || sellerUserId == null
        || buyerUserId < 1
        || sellerUserId < 1
        || buyerUserId.equals(sellerUserId)
        || buyerCharge < 0
        || sellerReceived < 1
        || tradeId == null
        || tradeId < 1) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
  }

  private record PointOperation(
      Long userId, PointTxType type, long amount, PointRefType refType, Long refId) {}
}
