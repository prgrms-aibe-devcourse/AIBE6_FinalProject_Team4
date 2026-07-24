package com.kiwobollae.api.point.controller;

import com.kiwobollae.api.point.service.WalletService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포인트", description = "포인트 잔액/내역 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/points")
public class PointController {

	private final WalletService walletService;
}
