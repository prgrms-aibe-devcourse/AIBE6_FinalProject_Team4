package com.kiwobollae.api.global.security;

import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Simple in-memory fixed-window rate limiter, keyed per (client IP, request path)
 * so each registered endpoint gets its own independent budget (see FilterConfig)
 * — a burst against one path never eats into another's allowance. A legitimate
 * user refreshing on page load/401 retries never gets close to typical limits.
 * Not a {@code @Component} on purpose: it's instantiated and URL-scoped
 * explicitly by FilterConfig instead of being auto-registered for "/*" by
 * Spring Boot.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final ObjectMapper objectMapper;
	private final int maxRequestsPerWindow;
	private final ConcurrentHashMap<String, Window> windowsByClient = new ConcurrentHashMap<>();

	public RateLimitFilter(ObjectMapper objectMapper, int maxRequestsPerWindow) {
		this.objectMapper = objectMapper;
		this.maxRequestsPerWindow = maxRequestsPerWindow;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String clientKey = resolveClientKey(request) + "|" + request.getRequestURI();
		Window window = windowsByClient.computeIfAbsent(clientKey, key -> new Window());

		if (window.tryConsume(maxRequestsPerWindow)) {
			filterChain.doFilter(request, response);
			return;
		}

		ErrorCode errorCode = ErrorCode.COMMON_RATE_LIMITED;
		ErrorResponse body = ErrorResponse.of(
				errorCode, errorCode.getDefaultMessage(), null, null,
				ErrorResponse.newTraceId(), request.getRequestURI());

		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}

	private String resolveClientKey(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private static final class Window {
		private volatile long windowStartMillis = System.currentTimeMillis();
		private final AtomicInteger count = new AtomicInteger(0);

		synchronized boolean tryConsume(int limit) {
			long now = System.currentTimeMillis();
			if (now - windowStartMillis >= WINDOW.toMillis()) {
				windowStartMillis = now;
				count.set(0);
			}
			return count.incrementAndGet() <= limit;
		}
	}
}
