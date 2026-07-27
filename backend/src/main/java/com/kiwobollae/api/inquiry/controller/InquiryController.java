package com.kiwobollae.api.inquiry.controller;

import com.kiwobollae.api.inquiry.service.InquiryService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1:1 문의", description = "고객 문의 등록/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/inquiries")
public class InquiryController {

	private final InquiryService inquiryService;
}
