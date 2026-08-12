package com.kiwobollae.api.commerce.cardmarket.dto;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import java.time.LocalDateTime;

public record CardMarketListingResponse(
    Long id,
    Long sellerUserId,
    String sellerNickname,
    Long cardId,
    Long goldenInstanceId,
    String cardCode,
    String cardName,
    TradingCardRarity rarity,
    String imageUrl,
    CardMarketAssetType assetType,
    Long askingPrice,
    CardMarketListingStatus status,
    long activeOfferCount,
    LocalDateTime expiresAt,
    LocalDateTime createdAt) {

  public static CardMarketListingResponse from(
      CardMarketListing listing, String imageUrl, long activeOfferCount) {
    return new CardMarketListingResponse(
        listing.getId(),
        listing.getSeller().getId(),
        listing.getSeller().getNickname(),
        listing.getCard().getId(),
        listing.getGoldenInstance() == null ? null : listing.getGoldenInstance().getId(),
        listing.getCard().getCode(),
        listing.getCard().getName(),
        listing.getCard().getRarity(),
        imageUrl,
        listing.getAssetType(),
        listing.getAskingPrice(),
        listing.getStatus(),
        activeOfferCount,
        listing.getExpiresAt(),
        listing.getCreatedAt());
  }
}
