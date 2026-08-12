package com.kiwobollae.api.commerce.cardmarket.dto;

public record CardMarketWalletResponse(
    Long paidPoint,
    Long freePoint,
    Long escrowedPaidPoint,
    String paidPointGuide,
    String freePointGuide) {}
