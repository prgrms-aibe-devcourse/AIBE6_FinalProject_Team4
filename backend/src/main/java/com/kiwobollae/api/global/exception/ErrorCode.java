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
	COMMON_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "동일한 키로 다른 내용의 요청이 이미 존재합니다."),
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

	// --- Domain codes (docs/error-codes.md §5) ---
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
	PRODUCT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "현재 구매할 수 없는 상품입니다."),
	PRODUCT_OUT_OF_STOCK(HttpStatus.UNPROCESSABLE_CONTENT, "재고가 부족합니다."),

	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
	ORDER_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없는 주문입니다."),

	CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
	CARD_NOT_OWNED(HttpStatus.UNPROCESSABLE_CONTENT, "교환에 필요한 카드를 보유하고 있지 않습니다."),

	EXCHANGE_NOT_FOUND(HttpStatus.NOT_FOUND, "교환 신청을 찾을 수 없습니다."),
	EXCHANGE_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없는 교환 신청입니다."),

	POINT_INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "사용 가능한 포인트가 부족합니다."),

	PAYMENT_DECLINED(HttpStatus.UNPROCESSABLE_CONTENT, "결제가 거절되었습니다."),
	PAYMENT_PROVIDER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "결제 대행사로부터 올바르지 않은 응답을 받았습니다."),
	PAYMENT_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "결제 대행사를 일시적으로 사용할 수 없습니다.");

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
