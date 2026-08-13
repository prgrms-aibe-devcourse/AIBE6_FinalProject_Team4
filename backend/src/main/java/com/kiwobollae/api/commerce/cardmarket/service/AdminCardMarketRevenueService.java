package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.dto.AdminCardMarketRevenueResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketTradeType;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCardMarketRevenueService {

  private static final int MAX_PAGE_SIZE = 100;
  private static final int CSV_PAGE_SIZE = 1_000;

  private final CardMarketTradeRepository tradeRepository;

  public AdminCardMarketRevenueResponse getRevenue(
      int page,
      int size,
      LocalDate from,
      LocalDate to,
      Long userId,
      Long cardId,
      CardMarketTradeType tradeType,
      String keyword) {
    validate(page, size, from, to, userId, cardId);
    LocalDateTime fromAt = startOfDay(from);
    LocalDateTime toAt = startOfDay(to == null ? null : to.plusDays(1));
    String normalizedKeyword = normalizeKeyword(keyword);
    Page<CardMarketTrade> trades =
        tradeRepository.searchAdmin(
            fromAt,
            toAt,
            userId,
            cardId,
            tradeType,
            normalizedKeyword,
            PageRequest.of(page, size, completedAtSort()));
    CardMarketTradeRepository.RevenueTotals totals =
        tradeRepository.summarizeAdmin(
            fromAt, toAt, userId, cardId, tradeType, normalizedKeyword);
    return AdminCardMarketRevenueResponse.from(
        trades,
        totals.getTotalTradeCount(),
        totals.getTotalTradePoint(),
        totals.getTotalFeePoint(),
        totals.getTotalSellerReceivedPoint());
  }

  public byte[] exportCsv(
      LocalDate from,
      LocalDate to,
      Long userId,
      Long cardId,
      CardMarketTradeType tradeType,
      String keyword) {
    validate(0, 1, from, to, userId, cardId);
    LocalDateTime fromAt = startOfDay(from);
    LocalDateTime toAt = startOfDay(to == null ? null : to.plusDays(1));
    String normalizedKeyword = normalizeKeyword(keyword);
    StringBuilder csv =
        new StringBuilder(
            "\uFEFF거래 ID,매물 ID,카드명,거래 유형,판매자 ID,판매자명,구매자 ID,구매자명,거래 금액,수수료,판매자 수령액,완료 시각\n");
    int page = 0;
    Page<CardMarketTrade> trades;
    do {
      trades =
          tradeRepository.searchAdmin(
              fromAt,
              toAt,
              userId,
              cardId,
              tradeType,
              normalizedKeyword,
              PageRequest.of(page, CSV_PAGE_SIZE, completedAtSort()));
      trades.getContent().forEach(trade -> appendCsvRow(csv, trade));
      page++;
    } while (trades.hasNext());
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void appendCsvRow(StringBuilder csv, CardMarketTrade trade) {
    List<String> values =
        List.of(
            String.valueOf(trade.getId()),
            String.valueOf(trade.getListing().getId()),
            trade.getCardNameSnapshot(),
            trade.getTradeType().name(),
            String.valueOf(trade.getSeller().getId()),
            trade.getSeller().getNickname(),
            String.valueOf(trade.getBuyer().getId()),
            trade.getBuyer().getNickname(),
            String.valueOf(trade.getTradePrice()),
            String.valueOf(trade.getFeePoint()),
            String.valueOf(trade.getSellerReceivedPoint()),
            trade.getCompletedAt().toString());
    csv.append(
        values.stream()
            .map(this::csvCell)
            .reduce((left, right) -> left + "," + right)
            .orElse(""));
    csv.append('\n');
  }

  private String csvCell(String value) {
    String safe = value == null ? "" : value;
    if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
      safe = "'" + safe;
    }
    return '"' + safe.replace("\"", "\"\"") + '"';
  }

  private String normalizeKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    String normalized = keyword.trim();
    if (normalized.length() > 50) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    return normalized;
  }

  private LocalDateTime startOfDay(LocalDate date) {
    if (date == null) {
      return null;
    }
    return date.atStartOfDay();
  }

  private Sort completedAtSort() {
    return Sort.by(Sort.Direction.DESC, "completedAt")
        .and(Sort.by(Sort.Direction.DESC, "id"));
  }

  private void validate(
      int page, int size, LocalDate from, LocalDate to, Long userId, Long cardId) {
    if (page < 0
        || size < 1
        || size > MAX_PAGE_SIZE
        || (from != null && to != null && from.isAfter(to))
        || (userId != null && userId < 1)
        || (cardId != null && cardId < 1)) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
  }
}
