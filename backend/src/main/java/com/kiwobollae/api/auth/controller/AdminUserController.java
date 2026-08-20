package com.kiwobollae.api.auth.controller;

import com.kiwobollae.api.admin.service.AdminUserManagementService;
import com.kiwobollae.api.auth.dto.request.AdminUserSuspendRequest;
import com.kiwobollae.api.auth.dto.response.AdminUserSummaryResponse;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.service.AdminUserQueryService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 회원", description = "관리자 전용 회원 검색/제재 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/user")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

	private final AdminUserQueryService adminUserQueryService;
	private final AdminUserManagementService adminUserManagementService;

	@Operation(summary = "관리자 회원 목록 조회", description = "회원 ID·이메일·닉네임·이름 검색과 상태 필터, 페이지네이션을 지원합니다. 누적 피신고 건수를 함께 반환합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<AdminUserSummaryResponse>>> getUsers(
			@RequestParam(required = false) @Size(max = 100) String keyword,
			@RequestParam(required = false) UserStatus status,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		return ResponseEntity.ok(ApiResponse.success(
				adminUserQueryService.search(keyword, status, pageable)
		));
	}

	@Operation(summary = "회원 정지", description = "부적절한 활동을 한 회원을 정지 처리합니다. 관리자·탈퇴 회원은 정지할 수 없습니다.")
	@PatchMapping("/{id}/suspend")
	public ResponseEntity<Void> suspendUser(
			@PathVariable Long id,
			@Valid @RequestBody AdminUserSuspendRequest request
	) {
		adminUserManagementService.suspendUser(id, request.reason());
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "회원 정지 해제", description = "정지 상태인 회원을 다시 활성 상태로 되돌립니다.")
	@PatchMapping("/{id}/reactivate")
	public ResponseEntity<Void> reactivateUser(@PathVariable Long id) {
		adminUserManagementService.reactivateUser(id);
		return ResponseEntity.noContent().build();
	}
}
