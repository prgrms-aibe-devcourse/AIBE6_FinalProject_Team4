package com.kiwobollae.api.content.controller;

import com.kiwobollae.api.content.service.PlantProfileService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내 식물", description = "반려 식물 등록/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/plants")
public class PlantController {

	private final PlantProfileService plantProfileService;
	private final PlantProfileService plantProfileService2;
}
