package com.kiwobollae.api.content.controller;

import com.kiwobollae.api.content.service.PlantJournalService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "성장 일지", description = "식물 성장 일지 작성/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/journals")
public class JournalController {

	private final PlantJournalService plantJournalService;
}
