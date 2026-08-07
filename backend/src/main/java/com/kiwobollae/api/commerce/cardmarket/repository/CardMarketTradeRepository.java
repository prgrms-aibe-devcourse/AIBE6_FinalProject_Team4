package com.kiwobollae.api.commerce.cardmarket.repository;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardMarketTradeRepository extends JpaRepository<CardMarketTrade, Long> {

  @EntityGraph(attributePaths = {"card", "seller", "buyer", "goldenInstance"})
  List<CardMarketTrade> findAllByBuyer_IdOrSeller_IdOrderByCompletedAtDesc(
      Long buyerId, Long sellerId);
}
