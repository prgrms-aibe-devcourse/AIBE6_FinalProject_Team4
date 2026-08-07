package com.kiwobollae.api.commerce.cardmarket.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CardMarketEntityPolicyTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 3, 0);

  @Test
  void counterOffersAlternateTurnWithoutLimitingSellerCounterCount() {
    CardMarketNegotiation negotiation =
        CardMarketNegotiation.builder()
            .status(CardMarketNegotiationStatus.NEGOTIATING)
            .turn(CardMarketParticipantType.SELLER)
            .currentProposerType(CardMarketParticipantType.BUYER)
            .currentPrice(1_000L)
            .escrowedPaidPoint(1_000L)
            .sellerCounterCount(0)
            .nextSequence(1)
            .expiresAt(NOW.plusHours(24))
            .createdAt(NOW)
            .updatedAt(NOW)
            .version(0L)
            .build();

    for (int count = 1; count <= 100; count++) {
      negotiation.updateProposal(
          CardMarketParticipantType.SELLER,
          2_000L,
          negotiation.getEscrowedPaidPoint(),
          NOW.plusHours(24),
          NOW);
      assertThat(negotiation.getTurn()).isEqualTo(CardMarketParticipantType.BUYER);
      negotiation.updateProposal(
          CardMarketParticipantType.BUYER,
          1_500L,
          1_500L,
          NOW.plusHours(24),
          NOW);
      assertThat(negotiation.getTurn()).isEqualTo(CardMarketParticipantType.SELLER);
    }

    assertThat(negotiation.getSellerCounterCount()).isEqualTo(100);
    assertThat(negotiation.getEscrowedPaidPoint()).isEqualTo(1_500L);
  }

  @Test
  void terminalNegotiationReleasesEscrowExactlyOnceInEntityState() {
    CardMarketNegotiation negotiation =
        CardMarketNegotiation.builder()
            .status(CardMarketNegotiationStatus.NEGOTIATING)
            .turn(CardMarketParticipantType.SELLER)
            .currentProposerType(CardMarketParticipantType.BUYER)
            .currentPrice(900L)
            .escrowedPaidPoint(900L)
            .sellerCounterCount(0)
            .nextSequence(2)
            .expiresAt(NOW.plusHours(24))
            .createdAt(NOW)
            .updatedAt(NOW)
            .version(0L)
            .build();

    long released =
        negotiation.closeAndRelease(CardMarketNegotiationStatus.REJECTED, "REJECTED", NOW);

    assertThat(released).isEqualTo(900L);
    assertThat(negotiation.getEscrowedPaidPoint()).isZero();
    assertThat(negotiation.getStatus()).isEqualTo(CardMarketNegotiationStatus.REJECTED);
    assertThat(negotiation.getClosedAt()).isEqualTo(NOW);
  }

  @Test
  void listingTracksSoldCancelledAndExpiredTerminalStates() {
    CardMarketListing sold = CardMarketListing.builder().status(CardMarketListingStatus.OPEN).build();
    CardMarketListing cancelled = CardMarketListing.builder().status(CardMarketListingStatus.OPEN).build();
    CardMarketListing expired = CardMarketListing.builder().status(CardMarketListingStatus.OPEN).build();

    sold.markSold("NEGOTIATED", NOW);
    cancelled.cancel("SELLER_CANCELLED", NOW);
    expired.expire(NOW);

    assertThat(sold.getStatus()).isEqualTo(CardMarketListingStatus.SOLD);
    assertThat(sold.getSoldAt()).isEqualTo(NOW);
    assertThat(cancelled.getStatus()).isEqualTo(CardMarketListingStatus.CANCELLED);
    assertThat(cancelled.getCancelledAt()).isEqualTo(NOW);
    assertThat(expired.getStatus()).isEqualTo(CardMarketListingStatus.EXPIRED);
    assertThat(expired.getClosedReason()).isEqualTo("EXPIRED");
  }
}
