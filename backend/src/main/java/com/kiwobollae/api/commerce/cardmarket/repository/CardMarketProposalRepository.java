package com.kiwobollae.api.commerce.cardmarket.repository;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketProposal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardMarketProposalRepository extends JpaRepository<CardMarketProposal, Long> {
  List<CardMarketProposal> findAllByNegotiation_IdOrderBySequenceNoAsc(Long negotiationId);

  List<CardMarketProposal> findAllByNegotiation_IdInOrderByNegotiation_IdAscSequenceNoAsc(
      List<Long> negotiationIds);
}
