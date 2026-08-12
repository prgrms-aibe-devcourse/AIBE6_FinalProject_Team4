package com.kiwobollae.api.commerce.cardmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class AdminCardMarketRevenueServiceTest {

  @Test
  void returnsFilteredPlatformRevenueSummaryAndPagedLedger() {
    CardMarketTradeRepository repository = mock(CardMarketTradeRepository.class);
    CardMarketTradeRepository.RevenueTotals totals =
        mock(CardMarketTradeRepository.RevenueTotals.class);
    AdminCardMarketRevenueService service = new AdminCardMarketRevenueService(repository);
    when(repository.searchAdmin(
            isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<CardMarketTrade>(List.of()));
    when(repository.summarizeAdmin(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
        .thenReturn(totals);
    when(totals.getTotalTradeCount()).thenReturn(3L);
    when(totals.getTotalTradePoint()).thenReturn(10_000L);
    when(totals.getTotalFeePoint()).thenReturn(2_000L);
    when(totals.getTotalSellerReceivedPoint()).thenReturn(8_000L);

    var response = service.getRevenue(0, 20, null, null, null, null, null, null);

    assertThat(response.totalTradeCount()).isEqualTo(3L);
    assertThat(response.totalTradePoint()).isEqualTo(10_000L);
    assertThat(response.totalFeePoint()).isEqualTo(2_000L);
    assertThat(response.totalSellerReceivedPoint()).isEqualTo(8_000L);
    verify(repository)
        .searchAdmin(
            isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  void rejectsOversizedRevenuePage() {
    AdminCardMarketRevenueService service =
        new AdminCardMarketRevenueService(mock(CardMarketTradeRepository.class));

    assertThatThrownBy(
            () -> service.getRevenue(0, 101, null, null, null, null, null, null))
        .isInstanceOf(BusinessException.class);
  }
}
