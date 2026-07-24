package com.kiwobollae.api.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
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
		ErrorResponse response = ErrorResponse.of(
				errorCode, message != null ? message : errorCode.getDefaultMessage(),
				details, fieldErrors, ErrorResponse.newTraceId(), request.getRequestURI());
		return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
	}
}
