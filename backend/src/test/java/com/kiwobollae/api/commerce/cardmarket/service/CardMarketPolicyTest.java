package com.kiwobollae.api.commerce.cardmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CardMarketPolicyTest {

  @Test
  void acceptsOnlyConfiguredPriceRange() {
    assertThat(CardMarketPolicy.requirePrice(100L)).isEqualTo(100L);
    assertThat(CardMarketPolicy.requirePrice(99_999_999L)).isEqualTo(99_999_999L);

    assertInvalidPrice(99L);
    assertInvalidPrice(100_000_000L);
    assertInvalidPrice(null);
  }

  @Test
  void appliesTwentyPercentFeeUsingIntegerPoints() {
    assertThat(CardMarketPolicy.fee(100L)).isEqualTo(20L);
    assertThat(CardMarketPolicy.fee(1_001L)).isEqualTo(200L);
    assertThat(1_001L - CardMarketPolicy.fee(1_001L)).isEqualTo(801L);
  }

  @Test
  void listingLastsSevenDaysAndOfferNeverOutlivesListing() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 6, 0, 0);
    LocalDateTime listingExpiry = CardMarketPolicy.listingExpiresAt(now);

    assertThat(listingExpiry).isEqualTo(now.plusDays(7));
    assertThat(CardMarketPolicy.negotiationExpiresAt(listingExpiry, now))
        .isEqualTo(now.plusHours(24));
    assertThat(CardMarketPolicy.negotiationExpiresAt(now.plusHours(3), now))
        .isEqualTo(now.plusHours(3));
  }

  private void assertInvalidPrice(Long price) {
    assertThatThrownBy(() -> CardMarketPolicy.requirePrice(price))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CARD_MARKET_PRICE_INVALID));
  }
}
