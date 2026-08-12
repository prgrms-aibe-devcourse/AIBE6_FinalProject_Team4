package com.kiwobollae.api.commerce.cardmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
  void returnsPlatformRevenueSummaryAndPagedLedger() {
    CardMarketTradeRepository repository = mock(CardMarketTradeRepository.class);
    AdminCardMarketRevenueService service = new AdminCardMarketRevenueService(repository);
    when(repository.findAllByOrderByCompletedAtDesc(any(Pageable.class)))
        .thenReturn(new PageImpl<CardMarketTrade>(List.of()));
    when(repository.sumTradePoint()).thenReturn(10_000L);
    when(repository.sumFeePoint()).thenReturn(2_000L);
    when(repository.sumSellerReceivedPoint()).thenReturn(8_000L);

    var response = service.getRevenue(0, 20);

    assertThat(response.totalTradePoint()).isEqualTo(10_000L);
    assertThat(response.totalFeePoint()).isEqualTo(2_000L);
    assertThat(response.totalSellerReceivedPoint()).isEqualTo(8_000L);
    verify(repository).findAllByOrderByCompletedAtDesc(any(Pageable.class));
  }

  @Test
  void rejectsOversizedRevenuePage() {
    AdminCardMarketRevenueService service =
        new AdminCardMarketRevenueService(mock(CardMarketTradeRepository.class));

    assertThatThrownBy(() -> service.getRevenue(0, 101))
        .isInstanceOf(BusinessException.class);
  }
}
