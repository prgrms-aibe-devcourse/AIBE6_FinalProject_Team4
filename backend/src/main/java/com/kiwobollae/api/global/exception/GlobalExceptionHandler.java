package com.kiwobollae.api.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into the ErrorResponse shape defined by docs/error-codes.md.
 * Note: authentication/authorization failures raised inside the Spring Security filter
 * chain (before the request reaches a controller) don't pass through here — those are
 * handled by JwtAuthenticationEntryPoint / JwtAccessDeniedHandler instead. The
 * AccessDeniedException handler below only catches denials raised from within a
 * controller (e.g. method-level @PreAuthorize).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
		if (e.getErrorCode() == ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS) {
			return ResponseEntity.status(e.getErrorCode().getHttpStatus())
					.header("Retry-After", "2")
					.body(errorResponse(e.getErrorCode(), e.getMessage(), e.getDetails(), null, request));
		}
		return respond(e.getErrorCode(), e.getMessage(), e.getDetails(), null, request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
		List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
				.toList();
		return respond(ErrorCode.COMMON_VALIDATION_FAILED, null, null, fieldErrors, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException e, HttpServletRequest request) {
		return respond(ErrorCode.COMMON_MALFORMED_JSON, null, null, null, request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
		return respond(ErrorCode.COMMON_UNSUPPORTED_MEDIA_TYPE, null, null, null, request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
		return respond(ErrorCode.COMMON_RESOURCE_NOT_FOUND, null, null, null, request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
		return respond(ErrorCode.AUTH_ACCESS_DENIED, null, null, null, request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
		// 유니크 제약 위반 등 DB 무결성 충돌. 대개 check-then-act 동시성 경쟁에서 발생하므로
		// 클라이언트에는 재시도 가능한 409로 응답한다. 다만 코드 결함으로 인한 위반도 여기로 올 수
		// 있으므로 원인 파악을 위해 traceId와 함께 로그를 남긴다.
		String traceId = ErrorResponse.newTraceId();
		log.warn("Data integrity violation [traceId={}] at {}", traceId, request.getRequestURI(), e);
		ErrorResponse response = ErrorResponse.of(
				ErrorCode.COMMON_DATA_CONFLICT, ErrorCode.COMMON_DATA_CONFLICT.getDefaultMessage(),
				null, null, traceId, request.getRequestURI());
		return ResponseEntity.status(ErrorCode.COMMON_DATA_CONFLICT.getHttpStatus()).body(response);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
		String traceId = ErrorResponse.newTraceId();
		log.error("Unhandled exception [traceId={}] at {}", traceId, request.getRequestURI(), e);
		ErrorResponse response = ErrorResponse.of(
				ErrorCode.COMMON_INTERNAL_ERROR, ErrorCode.COMMON_INTERNAL_ERROR.getDefaultMessage(),
				null, null, traceId, request.getRequestURI());
		return ResponseEntity.status(ErrorCode.COMMON_INTERNAL_ERROR.getHttpStatus()).body(response);
	}

	private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, String message, Map<String, Object> details,
			List<ErrorResponse.FieldError> fieldErrors, HttpServletRequest request) {
		return ResponseEntity.status(errorCode.getHttpStatus())
				.body(errorResponse(errorCode, message, details, fieldErrors, request));
	}

	private ErrorResponse errorResponse(ErrorCode errorCode, String message, Map<String, Object> details,
			List<ErrorResponse.FieldError> fieldErrors, HttpServletRequest request) {
		return ErrorResponse.of(
				errorCode, message != null ? message : errorCode.getDefaultMessage(),
				details, fieldErrors, ErrorResponse.newTraceId(), request.getRequestURI());
	}
}
