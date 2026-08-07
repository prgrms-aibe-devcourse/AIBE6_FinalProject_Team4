package com.kiwobollae.api.commerce.cardmarket.infrastructure;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.service.WalletService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CardMarketPointAdapterTest {

  @Test
  void offerUsesOnlyPaidPointThroughGenericPointPrimitive() {
    WalletService walletService = mock(WalletService.class);
    CardMarketPointAdapter adapter = new CardMarketPointAdapter(walletService);

    adapter.reserveOffer(7L, 600L, 81L);

    verify(walletService)
        .applyDelta(
            7L,
            PointTxType.MARKET_ESCROW,
            CurrencyType.PAID,
            -600L,
            PointRefType.MARKET_OFFER,
            81L);
  }

  @Test
  void settlementProcessesUsersInOrderAndReturnsOwnOfferBeforeFullPurchaseCharge() {
    WalletService walletService = mock(WalletService.class);
    CardMarketPointAdapter adapter = new CardMarketPointAdapter(walletService);

    adapter.settleTrade(
        5L,
        2L,
        1_000L,
        800L,
        71L,
        List.of(
            new CardMarketPointPort.OfferRelease(9L, 300L, 91L),
            new CardMarketPointPort.OfferRelease(5L, 900L, 81L)));

    InOrder order = inOrder(walletService);
    order
        .verify(walletService)
        .applyDelta(
            2L,
            PointTxType.MARKET_SALE,
            CurrencyType.PAID,
            800L,
            PointRefType.MARKET_TRADE,
            71L);
    order
        .verify(walletService)
        .applyDelta(
            5L,
            PointTxType.MARKET_RELEASE,
            CurrencyType.PAID,
            900L,
            PointRefType.MARKET_OFFER,
            81L);
    order
        .verify(walletService)
        .applyDelta(
            5L,
            PointTxType.MARKET_PURCHASE,
            CurrencyType.PAID,
            -1_000L,
            PointRefType.MARKET_TRADE,
            71L);
    order
        .verify(walletService)
        .applyDelta(
            9L,
            PointTxType.MARKET_RELEASE,
            CurrencyType.PAID,
            300L,
            PointRefType.MARKET_OFFER,
            91L);
  }
}
