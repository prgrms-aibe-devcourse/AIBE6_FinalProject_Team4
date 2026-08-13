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
	COMMON_FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일 용량이 너무 큽니다. 5MB 이하로 업로드해 주세요."),

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

	CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
	CART_QUANTITY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "장바구니에는 상품당 최대 99개까지 담을 수 있습니다."),
	CART_QUANTITY_EXCEEDS_STOCK(HttpStatus.UNPROCESSABLE_CONTENT, "재고보다 많은 수량은 담을 수 없습니다."),

	CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
	CARD_NOT_OWNED(HttpStatus.UNPROCESSABLE_CONTENT, "교환에 필요한 쿠폰을 보유하고 있지 않습니다."),
	GACHA_DRAW_NOT_FOUND(HttpStatus.NOT_FOUND, "가챠 결과를 찾을 수 없습니다."),
	GACHA_DRAW_NOT_COMPLETED(HttpStatus.CONFLICT, "아직 가챠 결과가 확정되지 않았습니다."),
	GACHA_MASTER_DATA_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "가챠 카드 데이터가 올바르지 않습니다."),
	GACHA_PROCESSING_CONFLICT(HttpStatus.CONFLICT, "이미 처리 중인 가챠 요청입니다."),
	GACHA_REWARD_MANUAL_REVIEW(HttpStatus.INTERNAL_SERVER_ERROR, "가챠 보상에 관리자 확인이 필요합니다."),
	GACHA_MANUAL_RETRY_INVALID_STATE(HttpStatus.CONFLICT, "관리자 재시도가 가능한 가챠 상태가 아닙니다."),
	GACHA_DISMANTLE_ITEM_INVALID(HttpStatus.BAD_REQUEST, "분해할 카드 정보가 올바르지 않습니다."),
	GACHA_CARD_NOT_OWNED(HttpStatus.UNPROCESSABLE_CONTENT, "보유하지 않은 가챠 카드입니다."),
	GACHA_CARD_NOT_DISMANTLABLE(HttpStatus.UNPROCESSABLE_CONTENT, "분해할 수 없는 등급의 카드입니다."),
	GACHA_CARD_KEEP_ONE_REQUIRED(HttpStatus.CONFLICT, "카드는 종류별로 최소 1장을 남겨야 합니다."),
	GACHA_SHARD_INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "카드 조각이 부족합니다."),
	GACHA_COSMETIC_NOT_FOUND(HttpStatus.NOT_FOUND, "칭호 또는 테두리를 찾을 수 없습니다."),
	GACHA_COSMETIC_ALREADY_OWNED(HttpStatus.CONFLICT, "이미 해금한 칭호 또는 테두리입니다."),
	GACHA_COSMETIC_NOT_OWNED(HttpStatus.UNPROCESSABLE_CONTENT, "해금하지 않은 칭호 또는 테두리입니다."),

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

	// --- AI domain ---
	AI_CONFIGURATION_INVALID(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능 설정이 완료되지 않았습니다."),
	AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스를 일시적으로 사용할 수 없습니다."),
	AI_REQUEST_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요."),
	AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI 서비스로부터 올바르지 않은 응답을 받았습니다."),

	// --- Content 도메인: 식물 프로필 / 성장 일지 (팀 컨벤션에 따라 메시지 구분 대신 전용 코드 사용) ---
	PLANT_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "식물 프로필을 찾을 수 없습니다."),
	PLANT_SPECIES_NOT_FOUND(HttpStatus.NOT_FOUND, "식물 종을 찾을 수 없습니다."),
	PLANT_SPECIES_DUPLICATE_NAME(HttpStatus.CONFLICT, "이미 등록된 이름의 식물 종입니다."),
	JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "성장 일지를 찾을 수 없습니다."),
	JOURNAL_DUPLICATE_IMAGE(HttpStatus.UNPROCESSABLE_CONTENT, "같은 날 이미 사용한 사진입니다."),
	JOURNAL_IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "jpg, png, webp 형식의 이미지만 업로드할 수 있습니다."),
	JOURNAL_IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "이미지 업로드에 실패했습니다. 잠시 후 다시 시도해 주세요."),
	PLANT_IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "jpg, png, webp 형식의 이미지만 업로드할 수 있습니다."),
	PLANT_IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "이미지 업로드에 실패했습니다. 잠시 후 다시 시도해 주세요."),
	TIMELAPSE_NOT_HARVESTED(HttpStatus.CONFLICT, "재배가 완료된 식물만 타임랩스를 만들 수 있습니다."),
	TIMELAPSE_INSUFFICIENT_IMAGES(HttpStatus.CONFLICT, "대표 이미지가 2장 이상 있어야 타임랩스를 만들 수 있습니다."),
	TIMELAPSE_ALREADY_PROCESSING(HttpStatus.CONFLICT, "이미 타임랩스를 생성하는 중입니다."),
	TIMELAPSE_VIDEO_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "타임랩스 영상 업로드에 실패했습니다. 잠시 후 다시 시도해 주세요."),

	// --- 마이페이지 / 배송지 도메인 ---
	ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "배송지를 찾을 수 없습니다."),
	ADDRESS_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "배송지는 최대 5개까지 등록할 수 있습니다."),

	// --- 문의 / 신고 도메인 ---
	INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
	INQUIRY_INVALID_STATE(HttpStatus.CONFLICT, "이미 답변이 완료된 문의입니다."),
	REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."),
	REPORT_INVALID_STATE(HttpStatus.CONFLICT, "이미 처리가 완료된 신고입니다."),
	REPORT_DUPLICATE_PENDING(HttpStatus.CONFLICT, "이미 처리 대기 중인 동일 신고가 있습니다."),

	// --- 알림 도메인 ---
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

	// --- 커뮤니티 게시판 도메인 ---
	BOARD_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
	BOARD_NOTICE_FORBIDDEN(HttpStatus.FORBIDDEN, "공지사항은 관리자만 작성할 수 있습니다."),
	BOARD_JOURNAL_REQUIRED(HttpStatus.BAD_REQUEST, "식물 Q&A는 연동할 일지를 선택해야 합니다."),
	BOARD_JOURNAL_NOT_OWNED(HttpStatus.UNPROCESSABLE_CONTENT, "본인이 작성한 일지만 연동할 수 있습니다."),
	BOARD_POST_NOT_OWNED(HttpStatus.FORBIDDEN, "본인이 작성한 게시글만 수정·삭제할 수 있습니다."),
	BOARD_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
	BOARD_COMMENT_NOT_OWNED(HttpStatus.FORBIDDEN, "본인이 작성한 댓글만 수정·삭제할 수 있습니다."),
	BOARD_ALREADY_LIKED(HttpStatus.CONFLICT, "이미 좋아요를 눌렀습니다."),
	BOARD_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "좋아요를 누르지 않았습니다."),
	BOARD_JOURNAL_NOT_LINKED(HttpStatus.NOT_FOUND, "연동된 성장 일지가 없습니다."),
	BOARD_IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "jpg, png, webp 형식의 이미지만 업로드할 수 있습니다."),
	BOARD_IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "이미지 업로드에 실패했습니다. 잠시 후 다시 시도해 주세요."),
	BOARD_IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "이미지는 1장까지 첨부할 수 있습니다.");

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
