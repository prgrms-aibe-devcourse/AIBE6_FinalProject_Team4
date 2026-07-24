package com.kiwobollae.api.auth.controller;

import com.kiwobollae.api.auth.dto.request.LoginRequest;
import com.kiwobollae.api.auth.dto.request.SignupRequest;
import com.kiwobollae.api.auth.dto.response.LoginResponse;
import com.kiwobollae.api.auth.dto.response.UserResponse;
import com.kiwobollae.api.auth.service.AuthService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입, 로그인 등 인증 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/auth")
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "회원가입", description = "이메일/비밀번호로 새 계정을 생성합니다.")
	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.signup(request)));
	}

	@Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 액세스 토큰을 발급받습니다.")
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
	}
}
