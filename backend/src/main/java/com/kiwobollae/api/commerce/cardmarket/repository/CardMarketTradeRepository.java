package com.kiwobollae.api.commerce.cardmarket.repository;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketTradeType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardMarketTradeRepository extends JpaRepository<CardMarketTrade, Long> {

  @EntityGraph(attributePaths = {"card", "seller", "buyer", "goldenInstance"})
  Page<CardMarketTrade> findAllByBuyer_IdOrSeller_Id(
      Long buyerId, Long sellerId, Pageable pageable);

  @EntityGraph(attributePaths = {"card", "seller", "buyer", "goldenInstance"})
  Page<CardMarketTrade> findAllByOrderByCompletedAtDesc(Pageable pageable);

  @Query("select coalesce(sum(t.tradePrice), 0) from CardMarketTrade t")
  long sumTradePoint();

  @Query("select coalesce(sum(t.feePoint), 0) from CardMarketTrade t")
  long sumFeePoint();

  @Query("select coalesce(sum(t.sellerReceivedPoint), 0) from CardMarketTrade t")
  long sumSellerReceivedPoint();

  @EntityGraph(attributePaths = {"card", "seller", "buyer", "goldenInstance", "listing"})
  @Query(
      value =
          """
          select t from CardMarketTrade t
          where (:fromAt is null or t.completedAt >= :fromAt)
            and (:toAt is null or t.completedAt < :toAt)
            and (:userId is null or t.seller.id = :userId or t.buyer.id = :userId)
            and (:cardId is null or t.card.id = :cardId)
            and (:tradeType is null or t.tradeType = :tradeType)
            and (:keyword is null or lower(t.cardNameSnapshot) like lower(concat('%', :keyword, '%')))
          """,
      countQuery =
          """
          select count(t) from CardMarketTrade t
          where (:fromAt is null or t.completedAt >= :fromAt)
            and (:toAt is null or t.completedAt < :toAt)
            and (:userId is null or t.seller.id = :userId or t.buyer.id = :userId)
            and (:cardId is null or t.card.id = :cardId)
            and (:tradeType is null or t.tradeType = :tradeType)
            and (:keyword is null or lower(t.cardNameSnapshot) like lower(concat('%', :keyword, '%')))
          """)
  Page<CardMarketTrade> searchAdmin(
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt,
      @Param("userId") Long userId,
      @Param("cardId") Long cardId,
      @Param("tradeType") CardMarketTradeType tradeType,
      @Param("keyword") String keyword,
      Pageable pageable);

  interface RevenueTotals {
    long getTotalTradeCount();

    long getTotalTradePoint();

    long getTotalFeePoint();

    long getTotalSellerReceivedPoint();
  }

  @Query(
      """
      select count(t) as totalTradeCount,
             coalesce(sum(t.tradePrice), 0) as totalTradePoint,
             coalesce(sum(t.feePoint), 0) as totalFeePoint,
             coalesce(sum(t.sellerReceivedPoint), 0) as totalSellerReceivedPoint
      from CardMarketTrade t
      where (:fromAt is null or t.completedAt >= :fromAt)
        and (:toAt is null or t.completedAt < :toAt)
        and (:userId is null or t.seller.id = :userId or t.buyer.id = :userId)
        and (:cardId is null or t.card.id = :cardId)
        and (:tradeType is null or t.tradeType = :tradeType)
        and (:keyword is null or lower(t.cardNameSnapshot) like lower(concat('%', :keyword, '%')))
      """)
  RevenueTotals summarizeAdmin(
      @Param("fromAt") LocalDateTime fromAt,
      @Param("toAt") LocalDateTime toAt,
      @Param("userId") Long userId,
      @Param("cardId") Long cardId,
      @Param("tradeType") CardMarketTradeType tradeType,
      @Param("keyword") String keyword);
}
