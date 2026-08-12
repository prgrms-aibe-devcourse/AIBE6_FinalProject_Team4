package com.kiwobollae.api.commerce.cardmarket.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

class CardMarketNotificationServiceTest {

  @Test
  void sendsOfferNotificationWithNegotiationDeepLinkToSeller() {
    NotificationService notificationService = mock(NotificationService.class);
    CardMarketNotificationService service =
        new CardMarketNotificationService(notificationService);
    CardMarketNegotiation negotiation = mock(CardMarketNegotiation.class);
    CardMarketListing listing = mock(CardMarketListing.class);
    TradingCard card = mock(TradingCard.class);
    User seller = mock(User.class);
    when(negotiation.getId()).thenReturn(77L);
    when(negotiation.getListing()).thenReturn(listing);
    when(listing.getSeller()).thenReturn(seller);
    when(listing.getCard()).thenReturn(card);
    when(seller.getId()).thenReturn(10L);
    when(card.getName()).thenReturn("황금 옥수수");

    service.offerCreated(negotiation);

    verify(notificationService)
        .notify(
            10L,
            NotificationType.CARD_MARKET,
            "새 가격 제안이 도착했어요",
            "황금 옥수수 카드의 가격 제안을 확인해 주세요.",
            "/card-market/negotiations/77",
            "CARD_MARKET_NEGOTIATION",
            77L);
  }
}
