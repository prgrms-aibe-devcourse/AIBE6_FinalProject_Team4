package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CardMarketNotificationService {

  private static final String NEGOTIATION_REF = "CARD_MARKET_NEGOTIATION";
  private static final String LISTING_REF = "CARD_MARKET_LISTING";
  private static final String TRADE_REF = "CARD_MARKET_TRADE";

  private final NotificationService notificationService;

  public void offerCreated(CardMarketNegotiation negotiation) {
    notifyNegotiation(
        negotiation.getListing().getSeller().getId(),
        negotiation,
        "새 가격 제안이 도착했어요",
        negotiation.getListing().getCard().getName() + " 카드의 가격 제안을 확인해 주세요.");
  }

  public void counterProposed(CardMarketNegotiation negotiation, Long proposerId) {
    Long receiverId =
        negotiation.getBuyer().getId().equals(proposerId)
            ? negotiation.getListing().getSeller().getId()
            : negotiation.getBuyer().getId();
    notifyNegotiation(
        receiverId,
        negotiation,
        "새로운 역제안이 도착했어요",
        negotiation.getListing().getCard().getName() + " 카드의 변경된 제안 가격을 확인해 주세요.");
  }

  public void negotiationRejected(CardMarketNegotiation negotiation, Long actorId) {
    Long receiverId =
        negotiation.getBuyer().getId().equals(actorId)
            ? negotiation.getListing().getSeller().getId()
            : negotiation.getBuyer().getId();
    notifyNegotiation(
        receiverId,
        negotiation,
        "가격 제안이 거절되었어요",
        negotiation.getListing().getCard().getName() + " 카드의 가격 제안이 종료되었습니다.");
  }

  public void negotiationCancelled(CardMarketNegotiation negotiation) {
    notifyNegotiation(
        negotiation.getListing().getSeller().getId(),
        negotiation,
        "구매자가 가격 제안을 취소했어요",
        negotiation.getListing().getCard().getName() + " 카드의 가격 제안이 취소되었습니다.");
  }

  public void negotiationClosed(CardMarketNegotiation negotiation, String reason) {
    String content =
        "LISTING_EXPIRED".equals(reason)
            ? "매물 등록 기간이 끝나 가격 제안이 종료되었습니다."
            : "매물 판매가 종료되어 가격 제안이 함께 종료되었습니다.";
    notifyNegotiation(
        negotiation.getBuyer().getId(),
        negotiation,
        "가격 제안이 종료되었어요",
        negotiation.getListing().getCard().getName() + " 카드: " + content);
  }

  public void negotiationExpired(CardMarketNegotiation negotiation) {
    notifyNegotiation(
        negotiation.getBuyer().getId(),
        negotiation,
        "가격 제안 기간이 끝났어요",
        negotiation.getListing().getCard().getName() + " 카드의 가격 제안이 만료되었습니다.");
    notifyNegotiation(
        negotiation.getListing().getSeller().getId(),
        negotiation,
        "가격 제안 기간이 끝났어요",
        negotiation.getListing().getCard().getName() + " 카드의 가격 제안이 만료되었습니다.");
  }

  public void listingExpired(CardMarketListing listing) {
    notificationService.notify(
        listing.getSeller().getId(),
        NotificationType.CARD_MARKET,
        "매물 등록 기간이 끝났어요",
        listing.getCard().getName() + " 카드가 판매되지 않아 보유 카드로 돌아왔습니다.",
        "/card-market?view=sell",
        LISTING_REF,
        listing.getId());
  }

  public void tradeCompleted(
      CardMarketListing listing, Long buyerId, Long tradeId, long tradePrice) {
    String content =
        listing.getCard().getName() + " 카드 거래가 " + String.format("%,d", tradePrice) + "P에 완료되었습니다.";
    notificationService.notify(
        listing.getSeller().getId(),
        NotificationType.CARD_MARKET,
        "카드가 판매되었어요",
        content,
        tradeUrl(),
        TRADE_REF,
        tradeId);
    notificationService.notify(
        buyerId,
        NotificationType.CARD_MARKET,
        "카드 구매가 완료되었어요",
        content,
        tradeUrl(),
        TRADE_REF,
        tradeId);
  }

  private void notifyNegotiation(
      Long userId, CardMarketNegotiation negotiation, String title, String content) {
    notificationService.notify(
        userId,
        NotificationType.CARD_MARKET,
        title,
        content,
        negotiationUrl(negotiation.getId()),
        NEGOTIATION_REF,
        negotiation.getId());
  }

  private String negotiationUrl(Long id) {
    return "/card-market/negotiations/" + id;
  }

  private String tradeUrl() {
    return "/card-market?view=trades";
  }
}
