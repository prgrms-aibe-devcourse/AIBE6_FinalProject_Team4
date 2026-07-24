package com.kiwobollae.api.admin.controller;

import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shell for admin-only endpoints. Admin business logic mostly lives in the owning domain's
 * service (inquiry answers, report processing, product/card management, etc.); this controller
 * is a starting point for the team to expand.
 */
@Tag(name = "관리자", description = "관리자 전용 API (주문/교환/상품/신고 관리 등)")
@RestController
@RequestMapping(ApiVersion.V1 + "/admin")
public class AdminController {
}
