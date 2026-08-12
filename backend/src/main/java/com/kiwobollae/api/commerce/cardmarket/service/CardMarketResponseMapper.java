package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketTradeResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.service.CommerceAssetUrlResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardMarketResponseMapper {

  private final CommerceAssetUrlResolver assetUrlResolver;

  public CardMarketListingResponse listing(CardMarketListing listing, long offerCount) {
    return CardMarketListingResponse.from(
        listing, assetUrlResolver.resolve(listing.getCard().getImageKey()), offerCount);
  }

  public CardMarketNegotiationResponse negotiation(
      CardMarketNegotiation negotiation, List<CardMarketProposalResponse> proposals) {
    return CardMarketNegotiationResponse.from(
        negotiation,
        assetUrlResolver.resolve(negotiation.getListing().getCard().getImageKey()),
        proposals);
  }

  public CardMarketTradeResponse trade(CardMarketTrade trade) {
    return CardMarketTradeResponse.from(
        trade, assetUrlResolver.resolve(trade.getImageKeySnapshot()));
  }

  public String imageUrl(String imageKey) {
    return assetUrlResolver.resolve(imageKey);
  }
}
