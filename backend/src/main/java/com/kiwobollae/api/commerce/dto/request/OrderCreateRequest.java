package com.kiwobollae.api.commerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderCreateRequest(
		@NotEmpty List<Long> cartItemIds,
		// 100원 단위, 0~주문 총액 범위 검증은 WalletService#deductForOrderPurchase에서 수행한다.
		@NotNull @Min(0) Long requestedFreePoint,
		@NotBlank @Size(max = 50) String receiverName,
		@NotBlank @Size(max = 20) String receiverPhone,
		@NotBlank @Size(max = 200) String address,
		@Size(max = 100) String addressDetail
) {
}
