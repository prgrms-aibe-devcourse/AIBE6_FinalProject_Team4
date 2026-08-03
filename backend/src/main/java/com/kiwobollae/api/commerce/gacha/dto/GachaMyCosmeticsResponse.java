package com.kiwobollae.api.commerce.gacha.dto;

import java.util.List;

public record GachaMyCosmeticsResponse(
    GachaShardWalletResponse shards, List<GachaCosmeticResponse> cosmetics) {}
