package com.kiwobollae.api.commerce.cardmarket.dto;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import java.time.LocalDateTime;
import java.util.List;

public record CardMarketNegotiationResponse(
    Long id,
    Long listingId,
    Long buyerUserId,
    String buyerNickname,
    Long sellerUserId,
    Long cardId,
    String cardName,
    String imageUrl,
    Long askingPrice,
    CardMarketNegotiationStatus status,
    CardMarketParticipantType turn,
    CardMarketParticipantType currentProposerType,
    Long currentPrice,
    Long escrowedPaidPoint,
    LocalDateTime expiresAt,
    List<CardMarketProposalResponse> proposals) {

  public static CardMarketNegotiationResponse from(
      CardMarketNegotiation negotiation,
      String imageUrl,
      List<CardMarketProposalResponse> proposals) {
    var listing = negotiation.getListing();
    return new CardMarketNegotiationResponse(
        negotiation.getId(),
        listing.getId(),
        negotiation.getBuyer().getId(),
        negotiation.getBuyer().getNickname(),
        listing.getSeller().getId(),
        listing.getCard().getId(),
        listing.getCard().getName(),
        imageUrl,
        listing.getAskingPrice(),
        negotiation.getStatus(),
        negotiation.getTurn(),
        negotiation.getCurrentProposerType(),
        negotiation.getCurrentPrice(),
        negotiation.getEscrowedPaidPoint(),
        negotiation.getExpiresAt(),
        proposals);
  }
}
