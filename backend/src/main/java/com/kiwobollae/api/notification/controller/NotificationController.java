package com.kiwobollae.api.notification.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.notification.dto.request.NotificationSettingRequest;
import com.kiwobollae.api.notification.dto.response.NotificationResponse;
import com.kiwobollae.api.notification.dto.response.NotificationSettingResponse;
import com.kiwobollae.api.notification.dto.response.UnreadCountResponse;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림", description = "알림 조회/읽음 처리 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/notifications")
public class NotificationController {

	// 클라이언트가 ?size=로 과도한 값을 보내 대량 조회를 유발하지 않도록 이 엔드포인트에서만 상한을 둔다.
	private static final int MAX_PAGE_SIZE = 100;

	private final NotificationService notificationService;

	@Operation(summary = "알림 목록 조회", description = "최신순 페이지네이션. type으로 배송/커뮤니티/재화/공지 등을 필터링할 수 있고, 보관 기간이 지난 알림은 제외됩니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) NotificationType type,
			@ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		return ResponseEntity.ok(ApiResponse.success(
				notificationService.getNotifications(userId, type, boundPageSize(pageable))
		));
	}

	private Pageable boundPageSize(Pageable pageable) {
		if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
	}

	@Operation(summary = "미읽음 알림 수 조회", description = "헤더 배지 표시용으로 isRead=false 알림 개수를 반환합니다.")
	@GetMapping("/unread-count")
	public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(@AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount(userId)));
	}

	@Operation(summary = "알림 읽음 처리", description = "본인 소유를 확인한 뒤 isRead=true로 바꾸고 linkUrl을 포함해 반환합니다.")
	@PatchMapping("/{notificationId}/read")
	public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long notificationId
	) {
		return ResponseEntity.ok(ApiResponse.success(notificationService.markRead(userId, notificationId)));
	}

	@Operation(summary = "전체 읽음 처리", description = "내 미읽음 알림을 모두 읽음 상태로 일괄 변경합니다.")
	@PatchMapping("/read-all")
	public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Long userId) {
		notificationService.markAllRead(userId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "알림 삭제", description = "본인 소유 알림을 삭제합니다.")
	@DeleteMapping("/{notificationId}")
	public ResponseEntity<Void> deleteNotification(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long notificationId
	) {
		notificationService.deleteNotification(userId, notificationId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "알림 수신 설정 조회", description = "유형별(배송/커뮤니티/재화/공지 등) 수신 여부를 조회합니다. 한 번도 변경하지 않은 유형은 기본값(수신 허용)으로 표시됩니다.")
	@GetMapping("/settings")
	public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> getSettings(
			@AuthenticationPrincipal Long userId
	) {
		return ResponseEntity.ok(ApiResponse.success(notificationService.getSettings(userId)));
	}

	@Operation(summary = "알림 수신 설정 변경", description = "유형별 enabled 값을 변경합니다. 이후 발송(알림 생성)에 즉시 반영됩니다.")
	@PatchMapping("/settings")
	public ResponseEntity<ApiResponse<NotificationSettingResponse>> updateSetting(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody NotificationSettingRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(
				notificationService.updateSetting(userId, request.type(), request.enabled())
		));
	}
}
