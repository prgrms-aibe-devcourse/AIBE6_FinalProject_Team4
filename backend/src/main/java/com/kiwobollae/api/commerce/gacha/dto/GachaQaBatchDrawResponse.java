package com.kiwobollae.api.commerce.gacha.dto;

import java.util.List;

public record GachaQaBatchDrawResponse(List<Long> drawIds, int packCount) {}
