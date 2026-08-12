package com.kiwobollae.api.commerce.cardmarket.repository;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardMarketListingRepository extends JpaRepository<CardMarketListing, Long> {

  @EntityGraph(attributePaths = {"seller", "card", "goldenInstance"})
  @Query(
      """
      select l from CardMarketListing l
      where l.status = :status
        and l.expiresAt > :now
        and l.card.status = com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus.ACTIVE
        and (:assetType is null or l.assetType = :assetType)
        and (:cardId is null or l.card.id = :cardId)
        and (:keyword is null or lower(l.card.name) like lower(concat('%', :keyword, '%')))
      """)
  Page<CardMarketListing> search(
      @Param("status") CardMarketListingStatus status,
      @Param("assetType") CardMarketAssetType assetType,
      @Param("cardId") Long cardId,
      @Param("keyword") String keyword,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  @EntityGraph(attributePaths = {"seller", "card", "goldenInstance"})
  @Query("select l from CardMarketListing l where l.id = :id")
  Optional<CardMarketListing> findWithDetailsById(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from CardMarketListing l where l.id = :id")
  Optional<CardMarketListing> findByIdForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = {"seller", "card", "goldenInstance"})
  Page<CardMarketListing> findAllBySeller_IdAndStatus(
      Long sellerId, CardMarketListingStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"seller", "card", "goldenInstance"})
  Page<CardMarketListing> findAllBySeller_Id(Long sellerId, Pageable pageable);

  long countBySeller_IdAndStatus(Long sellerId, CardMarketListingStatus status);

  boolean existsByGoldenInstance_IdAndStatus(Long goldenInstanceId, CardMarketListingStatus status);

  List<CardMarketListing> findAllByStatusAndExpiresAtLessThanEqual(
      CardMarketListingStatus status, LocalDateTime now, Pageable pageable);

  List<CardMarketListing> findAllByStatusAndCard_StatusNot(
      CardMarketListingStatus status, TradingCardStatus cardStatus, Pageable pageable);
}
