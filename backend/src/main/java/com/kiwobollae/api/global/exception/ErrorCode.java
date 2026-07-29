package com.kiwobollae.api.global.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes and HTTP status mapping. Source of truth: docs/error-codes.md.
 * Naming convention: {@code {DOMAIN}_{REASON}} upper snake case; {@code COMMON_} is
 * reserved for cross-cutting infra errors, everything else uses its owning domain's
 * prefix. A code's meaning never changes once shipped — add a new code instead of
 * repurposing an old one.
 */
public enum ErrorCode {

	// --- Common / infra (docs/error-codes.md §4) ---
	COMMON_MALFORMED_JSON(HttpStatus.BAD_REQUEST, "요청 본문의 JSON 형식이 올바르지 않습니다."),
	COMMON_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	COMMON_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	COMMON_OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "다른 요청에 의해 데이터가 변경되었습니다. 최신 상태를 다시 조회해 주세요."),
	COMMON_DATA_CONFLICT(HttpStatus.CONFLICT, "데이터 제약 조건에 의해 요청이 충돌했습니다. 잠시 후 다시 시도해 주세요."),
	COMMON_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "동일한 키로 다른 내용의 요청이 이미 존재합니다."),
	COMMON_IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "동일한 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요."),
	COMMON_UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
	COMMON_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

	// --- Auth / session (docs/error-codes.md §4) ---
	AUTH_AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다. 로그인 후 다시 시도해 주세요."),
	AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
	AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다. 다시 로그인해 주세요."),
	AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다."),

	// --- Auth domain business errors — not yet in docs/error-codes.md's table, added
	// following the same {DOMAIN}_{REASON} convention as the AUTH_* session codes above. ---
	AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 다시 확인해 주세요."),
	AUTH_ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "계정이 활성 상태가 아닙니다."),
	AUTH_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	AUTH_NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	AUTH_EMAIL_NOT_VERIFIED(HttpStatus.CONFLICT, "이메일 인증을 먼저 완료해 주세요."),
	AUTH_VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증코드가 올바르지 않습니다."),
	AUTH_VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증코드가 만료되었습니다. 다시 요청해 주세요."),
	AUTH_VERIFICATION_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다. 코드를 다시 요청해 주세요."),
	// 401이 아니라 400인 이유: 이 값은 액세스 토큰 상태와 무관한 순수 입력값 검증 실패라, 401로 두면
	// 프론트의 "액세스 토큰 만료 → 자동 재발급 후 재시도" 인터셉터가 이를 세션 만료로 오인해 로그아웃시킨다.
	AUTH_CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
	AUTH_SOCIAL_ACCOUNT_HAS_NO_PASSWORD(HttpStatus.CONFLICT, "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),
	AUTH_EMAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "가입된 계정을 찾을 수 없습니다."),
	AUTH_OAUTH_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 provider입니다."),
	AUTH_OAUTH_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인 인증에 실패했습니다."),
	AUTH_OAUTH_EMAIL_REQUIRED(HttpStatus.CONFLICT, "이메일 제공에 동의해야 소셜 로그인을 사용할 수 있습니다."),
	AUTH_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "비밀번호 재설정 인증이 유효하지 않습니다. 인증을 다시 진행해 주세요."),

	// --- Domain codes (docs/error-codes.md §5) ---
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
	PRODUCT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "현재 구매할 수 없는 상품입니다."),
	PRODUCT_OUT_OF_STOCK(HttpStatus.UNPROCESSABLE_CONTENT, "재고가 부족합니다."),

	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
	ORDER_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없는 주문입니다."),

	CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
	CARD_NOT_OWNED(HttpStatus.UNPROCESSABLE_CONTENT, "교환에 필요한 카드를 보유하고 있지 않습니다."),

	EXCHANGE_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "교환 상품을 찾을 수 없습니다."),
	EXCHANGE_PRODUCT_OUT_OF_STOCK(HttpStatus.UNPROCESSABLE_CONTENT, "교환 상품의 재고가 부족합니다."),
	EXCHANGE_NOT_FOUND(HttpStatus.NOT_FOUND, "교환 신청을 찾을 수 없습니다."),
	EXCHANGE_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없는 교환 신청입니다."),

	POINT_INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "사용 가능한 포인트가 부족합니다."),
	POINT_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 지갑을 찾을 수 없습니다."),
	POINT_DUPLICATE_TRANSACTION(HttpStatus.CONFLICT, "이미 처리된 포인트 거래입니다."),

	PAYMENT_CHARGE_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "충전 상품을 찾을 수 없습니다."),
	PAYMENT_CHARGE_PRODUCT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "현재 구매할 수 없는 충전 상품입니다."),
	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 건을 찾을 수 없습니다."),
	PAYMENT_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 결제를 처리할 수 없습니다."),
	PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "승인 금액이 결제 요청 금액과 일치하지 않습니다."),
	PAYMENT_DECLINED(HttpStatus.UNPROCESSABLE_CONTENT, "결제가 거절되었습니다."),
	PAYMENT_PROVIDER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "결제 대행사로부터 올바르지 않은 응답을 받았습니다."),
	PAYMENT_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "결제 대행사를 일시적으로 사용할 수 없습니다."),

	// --- Content 도메인: 식물 프로필 / 성장 일지 (팀 컨벤션에 따라 메시지 구분 대신 전용 코드 사용) ---
	PLANT_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "식물 프로필을 찾을 수 없습니다."),
	PLANT_SPECIES_NOT_FOUND(HttpStatus.NOT_FOUND, "식물 종을 찾을 수 없습니다."),
	JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "성장 일지를 찾을 수 없습니다."),
	JOURNAL_DUPLICATE_IMAGE(HttpStatus.UNPROCESSABLE_CONTENT, "같은 날 이미 사용한 사진입니다."),

	// --- 문의 / 신고 도메인 ---
	INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
	INQUIRY_INVALID_STATE(HttpStatus.CONFLICT, "이미 답변이 완료된 문의입니다."),
	REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."),
	REPORT_INVALID_STATE(HttpStatus.CONFLICT, "이미 처리가 완료된 신고입니다."),
	REPORT_DUPLICATE_PENDING(HttpStatus.CONFLICT, "이미 처리 대기 중인 동일 신고가 있습니다.");

	private final HttpStatus httpStatus;
	private final String defaultMessage;

	ErrorCode(HttpStatus httpStatus, String defaultMessage) {
		this.httpStatus = httpStatus;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public String getDefaultMessage() {
		return defaultMessage;
	}
}
