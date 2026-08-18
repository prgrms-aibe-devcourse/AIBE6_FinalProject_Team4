package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketPageResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketProposalRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CardMarketQueryResponseAssembler {

  private final CardMarketNegotiationRepository negotiationRepository;
  private final CardMarketProposalRepository proposalRepository;
  private final CardMarketResponseMapper responseMapper;

  CardMarketListingResponse listing(CardMarketListing listing) {
    long offerCount =
        negotiationRepository.countByListing_IdAndStatus(
            listing.getId(), CardMarketNegotiationStatus.NEGOTIATING);
    return responseMapper.listing(listing, offerCount);
  }

  CardMarketPageResponse<CardMarketListingResponse> listingPage(Page<CardMarketListing> page) {
    List<Long> listingIds = page.getContent().stream().map(CardMarketListing::getId).toList();
    Map<Long, Long> offerCounts =
        listingIds.isEmpty()
            ? Map.of()
            : negotiationRepository
                .countByListingIdsAndStatus(
                    listingIds, CardMarketNegotiationStatus.NEGOTIATING)
                .stream()
                .collect(
                    Collectors.toMap(
                        CardMarketNegotiationRepository.ListingOfferCount::getListingId,
                        CardMarketNegotiationRepository.ListingOfferCount::getOfferCount));
    List<CardMarketListingResponse> content =
        page.getContent().stream()
            .map(
                listing ->
                    responseMapper.listing(
                        listing, offerCounts.getOrDefault(listing.getId(), 0L)))
            .toList();
    return page(page, content);
  }

  CardMarketNegotiationResponse negotiation(CardMarketNegotiation negotiation) {
    List<CardMarketProposalResponse> proposals =
        proposalRepository.findAllByNegotiation_IdOrderBySequenceNoAsc(negotiation.getId()).stream()
            .map(CardMarketProposalResponse::from)
            .toList();
    return responseMapper.negotiation(negotiation, proposals);
  }

  CardMarketPageResponse<CardMarketNegotiationResponse> negotiationPage(
      Page<CardMarketNegotiation> page) {
    List<Long> negotiationIds =
        page.getContent().stream().map(CardMarketNegotiation::getId).toList();
    Map<Long, List<CardMarketProposalResponse>> proposalsByNegotiation =
        negotiationIds.isEmpty()
            ? Map.of()
            : proposalRepository
                .findAllByNegotiation_IdInOrderByNegotiation_IdAscSequenceNoAsc(negotiationIds)
                .stream()
                .collect(
                    Collectors.groupingBy(
                        proposal -> proposal.getNegotiation().getId(),
                        Collectors.mapping(
                            CardMarketProposalResponse::from, Collectors.toList())));
    List<CardMarketNegotiationResponse> content =
        page.getContent().stream()
            .map(
                negotiation ->
                    responseMapper.negotiation(
                        negotiation,
                        proposalsByNegotiation.getOrDefault(negotiation.getId(), List.of())))
            .toList();
    return page(page, content);
  }

  private <S, T> CardMarketPageResponse<T> page(Page<S> source, List<T> content) {
    return new CardMarketPageResponse<>(
        content,
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages());
  }
}
