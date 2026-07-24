package com.kiwobollae.api.global.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response body shape defined by docs/error-codes.md §1.
 */
public record ErrorResponse(
		String code,
		String message,
		Map<String, Object> details,
		List<FieldError> fieldErrors,
		String traceId,
		String timestamp,
		String path
) {

	public record FieldError(String field, String reason) {
	}

	public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, Object> details,
			List<FieldError> fieldErrors, String traceId, String path) {
		return new ErrorResponse(
				errorCode.name(),
				message,
				details,
				fieldErrors == null ? List.of() : fieldErrors,
				traceId,
				Instant.now().toString(),
				path
		);
	}

	public static String newTraceId() {
		return UUID.randomUUID().toString();
	}
}
