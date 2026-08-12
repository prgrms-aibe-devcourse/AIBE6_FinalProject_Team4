package com.kiwobollae.api.commerce.cardmarket.dto;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketProposal;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketMessageCode;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import java.time.LocalDateTime;

public record CardMarketProposalResponse(
    Long id,
    Long proposerUserId,
    CardMarketParticipantType proposerType,
    Integer sequenceNo,
    Long proposedPrice,
    CardMarketMessageCode messageCode,
    LocalDateTime createdAt) {

  public static CardMarketProposalResponse from(CardMarketProposal proposal) {
    return new CardMarketProposalResponse(
        proposal.getId(),
        proposal.getProposer().getId(),
        proposal.getProposerType(),
        proposal.getSequenceNo(),
        proposal.getProposedPrice(),
        proposal.getMessageCode(),
        proposal.getCreatedAt());
  }
}
