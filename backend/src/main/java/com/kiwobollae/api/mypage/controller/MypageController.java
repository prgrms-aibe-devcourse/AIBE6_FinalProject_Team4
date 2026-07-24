package com.kiwobollae.api.mypage.controller;

import com.kiwobollae.api.mypage.service.UserAddressService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지", description = "내 정보, 배송지 등 마이페이지 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/mypage")
public class MypageController {

	private final UserAddressService userAddressService;
}
