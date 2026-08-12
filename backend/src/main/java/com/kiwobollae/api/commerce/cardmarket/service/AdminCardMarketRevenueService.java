package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.dto.AdminCardMarketRevenueResponse;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCardMarketRevenueService {

  private static final int MAX_PAGE_SIZE = 100;

  private final CardMarketTradeRepository tradeRepository;

  public AdminCardMarketRevenueResponse getRevenue(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(
          ErrorCode.COMMON_VALIDATION_FAILED,
          Map.of("reason", "page는 0 이상, size는 1~100이어야 합니다."));
    }
    return AdminCardMarketRevenueResponse.from(
        tradeRepository.findAllByOrderByCompletedAtDesc(PageRequest.of(page, size)),
        tradeRepository.sumTradePoint(),
        tradeRepository.sumFeePoint(),
        tradeRepository.sumSellerReceivedPoint());
  }
}
