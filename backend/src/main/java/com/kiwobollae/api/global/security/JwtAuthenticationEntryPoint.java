package com.kiwobollae.api.global.security;

import tools.jackson.databind.ObjectMapper;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Writes the docs/error-codes.md ErrorResponse shape for requests that reach the
 * security filter chain without a valid token. This runs before the DispatcherServlet,
 * so GlobalExceptionHandler never sees these — it has to be handled here directly.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws java.io.IOException {
		// JwtAuthenticationFilter stashes *why* a present-but-rejected token failed
		// (expired vs invalid/tampered); no attribute at all means no token was sent.
		Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE);
		ErrorCode errorCode = attribute instanceof ErrorCode code ? code : ErrorCode.AUTH_AUTHENTICATION_REQUIRED;
		ErrorResponse body = ErrorResponse.of(
				errorCode, errorCode.getDefaultMessage(), null, null,
				ErrorResponse.newTraceId(), request.getRequestURI());

		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}
}
