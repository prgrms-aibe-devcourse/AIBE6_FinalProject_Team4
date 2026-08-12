package com.kiwobollae.api.commerce.cardmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class CardMarketExpirySchedulerTest {

  @Test
  void processesOnlyConfiguredBatchForEachExpiryType() {
    CardMarketListingRepository listingRepository = mock(CardMarketListingRepository.class);
    CardMarketNegotiationRepository negotiationRepository =
        mock(CardMarketNegotiationRepository.class);
    CardMarketExpiryProcessor processor = mock(CardMarketExpiryProcessor.class);
    CardMarketExpiryScheduler scheduler =
        new CardMarketExpiryScheduler(
            listingRepository,
            negotiationRepository,
            processor,
            Clock.fixed(Instant.parse("2026-08-11T15:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(scheduler, "batchSize", 25);
    CardMarketNegotiation negotiation = mock(CardMarketNegotiation.class);
    CardMarketListing expiredListing = mock(CardMarketListing.class);
    CardMarketListing hiddenListing = mock(CardMarketListing.class);
    when(negotiation.getId()).thenReturn(1L);
    when(expiredListing.getId()).thenReturn(2L);
    when(hiddenListing.getId()).thenReturn(3L);
    when(negotiationRepository.findAllByStatusAndExpiresAtLessThanEqual(
            eq(CardMarketNegotiationStatus.NEGOTIATING), any(LocalDateTime.class), any(Pageable.class)))
        .thenReturn(List.of(negotiation));
    when(listingRepository.findAllByStatusAndExpiresAtLessThanEqual(
            eq(CardMarketListingStatus.OPEN), any(LocalDateTime.class), any(Pageable.class)))
        .thenReturn(List.of(expiredListing));
    when(listingRepository.findAllByStatusAndCard_StatusNot(
            eq(CardMarketListingStatus.OPEN), eq(TradingCardStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(List.of(hiddenListing));

    scheduler.expireDueResources();

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(negotiationRepository)
        .findAllByStatusAndExpiresAtLessThanEqual(
            eq(CardMarketNegotiationStatus.NEGOTIATING), any(LocalDateTime.class), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
    verify(processor).expireNegotiation(1L);
    verify(processor).expireListing(2L);
    verify(processor).closeHiddenCardListing(3L);
  }
}
