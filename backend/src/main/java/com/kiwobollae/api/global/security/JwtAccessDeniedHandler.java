package com.kiwobollae.api.global.security;

import tools.jackson.databind.ObjectMapper;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes the docs/error-codes.md ErrorResponse shape for authenticated requests that
 * are denied by the security filter chain (e.g. authorizeHttpRequests rules). Runs
 * before the DispatcherServlet, so GlobalExceptionHandler never sees these.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
			throws java.io.IOException {
		ErrorCode errorCode = ErrorCode.AUTH_ACCESS_DENIED;
		ErrorResponse body = ErrorResponse.of(
				errorCode, errorCode.getDefaultMessage(), null, null,
				ErrorResponse.newTraceId(), request.getRequestURI());

		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}
}
