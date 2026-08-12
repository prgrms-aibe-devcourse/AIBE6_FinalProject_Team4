package com.kiwobollae.api.commerce.cardmarket.repository;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CardMarketTradeRepository extends JpaRepository<CardMarketTrade, Long> {

  @EntityGraph(attributePaths = {"card", "seller", "buyer", "goldenInstance"})
  List<CardMarketTrade> findAllByBuyer_IdOrSeller_IdOrderByCompletedAtDesc(
      Long buyerId, Long sellerId);

  @EntityGraph(attributePaths = {"card", "seller", "buyer", "goldenInstance"})
  Page<CardMarketTrade> findAllByOrderByCompletedAtDesc(Pageable pageable);

  @Query("select coalesce(sum(t.tradePrice), 0) from CardMarketTrade t")
  long sumTradePoint();

  @Query("select coalesce(sum(t.feePoint), 0) from CardMarketTrade t")
  long sumFeePoint();

  @Query("select coalesce(sum(t.sellerReceivedPoint), 0) from CardMarketTrade t")
  long sumSellerReceivedPoint();
}
