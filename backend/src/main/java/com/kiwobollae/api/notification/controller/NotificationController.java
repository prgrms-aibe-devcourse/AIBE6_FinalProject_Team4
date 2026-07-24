package com.kiwobollae.api.notification.controller;

import com.kiwobollae.api.notification.service.NotificationService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림", description = "알림 조회/읽음 처리 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/notifications")
public class NotificationController {

	private final NotificationService notificationService;
}
