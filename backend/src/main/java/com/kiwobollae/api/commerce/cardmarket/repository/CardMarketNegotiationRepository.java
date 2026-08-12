package com.kiwobollae.api.commerce.cardmarket.repository;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardMarketNegotiationRepository
    extends JpaRepository<CardMarketNegotiation, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from CardMarketNegotiation n where n.id = :id")
  Optional<CardMarketNegotiation> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select n from CardMarketNegotiation n where n.listing.id = :listingId and n.status = :status order by n.buyer.id")
  List<CardMarketNegotiation> findAllByListingIdAndStatusForUpdate(
      @Param("listingId") Long listingId,
      @Param("status") CardMarketNegotiationStatus status);

  @EntityGraph(attributePaths = {"listing", "listing.card", "listing.seller"})
  Page<CardMarketNegotiation> findAllByBuyer_Id(Long buyerId, Pageable pageable);

  @EntityGraph(attributePaths = {"listing", "listing.card", "buyer"})
  Page<CardMarketNegotiation> findAllByListing_Seller_Id(Long sellerId, Pageable pageable);

  @EntityGraph(attributePaths = {"listing", "listing.card", "listing.seller", "buyer"})
  @Query(
      "select n from CardMarketNegotiation n where n.id = :id and (n.buyer.id = :userId or n.listing.seller.id = :userId)")
  Optional<CardMarketNegotiation> findOwnedDetailsById(
      @Param("id") Long id, @Param("userId") Long userId);

  boolean existsByListing_IdAndBuyer_IdAndStatus(
      Long listingId, Long buyerId, CardMarketNegotiationStatus status);

  long countByBuyer_IdAndStatus(Long buyerId, CardMarketNegotiationStatus status);

  long countByListing_IdAndStatus(Long listingId, CardMarketNegotiationStatus status);

  @Query(
      "select n.listing.id as listingId, count(n.id) as offerCount from CardMarketNegotiation n where n.listing.id in :listingIds and n.status = :status group by n.listing.id")
  List<ListingOfferCount> countByListingIdsAndStatus(
      @Param("listingIds") List<Long> listingIds,
      @Param("status") CardMarketNegotiationStatus status);

  @Query(
      "select coalesce(sum(n.escrowedPaidPoint), 0) from CardMarketNegotiation n where n.buyer.id = :buyerId and n.status = :status")
  long sumEscrowedPoint(
      @Param("buyerId") Long buyerId,
      @Param("status") CardMarketNegotiationStatus status);

  List<CardMarketNegotiation> findAllByStatusAndExpiresAtLessThanEqual(
      CardMarketNegotiationStatus status, LocalDateTime now, Pageable pageable);

  interface ListingOfferCount {
    Long getListingId();

    long getOfferCount();
  }
}
