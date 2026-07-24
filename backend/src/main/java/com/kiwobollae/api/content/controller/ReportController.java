package com.kiwobollae.api.content.controller;

import com.kiwobollae.api.content.service.ReportService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "신고", description = "부적절한 콘텐츠 신고 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/reports")
public class ReportController {

	private final ReportService reportService;
}
