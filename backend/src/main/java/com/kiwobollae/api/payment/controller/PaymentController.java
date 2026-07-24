package com.kiwobollae.api.payment.controller;

import com.kiwobollae.api.payment.service.PaymentService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "포인트 충전/결제 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/payments")
public class PaymentController {

	private final PaymentService paymentService;
}
