package com.kiwobollae.api.mypage.controller;

import com.kiwobollae.api.mypage.dto.request.UserAddressRequest;
import com.kiwobollae.api.mypage.dto.response.UserAddressResponse;
import com.kiwobollae.api.mypage.service.UserAddressService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지", description = "내 정보, 배송지 등 마이페이지 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/mypage")
public class MypageController {

	private final UserAddressService userAddressService;

	@Operation(summary = "배송지 목록 조회", description = "내 배송지를 기본 배송지 우선, 최신순으로 조회합니다.")
	@GetMapping("/addresses")
	public ResponseEntity<ApiResponse<List<UserAddressResponse>>> getAddresses(
			@AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(ApiResponse.success(userAddressService.getAddresses(userId)));
	}

	@Operation(summary = "배송지 등록", description = "새 배송지를 등록합니다. isDefault가 true면 기존 기본 배송지는 해제됩니다.")
	@PostMapping("/addresses")
	public ResponseEntity<ApiResponse<UserAddressResponse>> createAddress(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody UserAddressRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(userAddressService.createAddress(userId, request)));
	}

	@Operation(summary = "배송지 수정", description = "본인 소유 배송지의 정보를 수정합니다.")
	@PatchMapping("/addresses/{addressId}")
	public ResponseEntity<ApiResponse<UserAddressResponse>> updateAddress(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long addressId,
			@Valid @RequestBody UserAddressRequest request) {
		return ResponseEntity.ok(ApiResponse.success(userAddressService.updateAddress(userId, addressId, request)));
	}

	@Operation(summary = "배송지 삭제", description = "본인 소유 배송지를 삭제합니다.")
	@DeleteMapping("/addresses/{addressId}")
	public ResponseEntity<ApiResponse<Void>> deleteAddress(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long addressId) {
		userAddressService.deleteAddress(userId, addressId);
		return ResponseEntity.ok(ApiResponse.<Void>success(null));
	}

	@Operation(summary = "기본 배송지 설정", description = "지정한 배송지를 기본 배송지로 설정하고, 기존 기본 배송지는 해제합니다.")
	@PatchMapping("/addresses/{addressId}/default")
	public ResponseEntity<ApiResponse<UserAddressResponse>> setDefaultAddress(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long addressId) {
		return ResponseEntity.ok(ApiResponse.success(userAddressService.setDefaultAddress(userId, addressId)));
	}
}
