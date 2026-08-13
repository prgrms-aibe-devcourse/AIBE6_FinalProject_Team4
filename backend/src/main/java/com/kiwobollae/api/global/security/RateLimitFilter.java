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
import java.util.function.Predicate;
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
	private final Predicate<HttpServletRequest> requestMatcher;
	// null이면 기존처럼 요청 URI 그대로를 버킷 키로 쓴다. 경로 변수가 있는 엔드포인트
	// (예: /board/posts/{id}/comments)는 URI 그대로 쓰면 id별로 버킷이 갈라져 여러 글에
	// 나눠 스팸을 뿌리면 각각 새 예산으로 통과해버린다 — 그런 엔드포인트는 고정된 이름을
	// 줘서 같은 사용자의 모든 요청이 하나의 버킷을 공유하게 한다.
	private final String bucketName;
	private final ConcurrentHashMap<String, Window> windowsByClient = new ConcurrentHashMap<>();

	public RateLimitFilter(ObjectMapper objectMapper, int maxRequestsPerWindow) {
		this(objectMapper, maxRequestsPerWindow, request -> true);
	}

	// urlPatterns의 servlet 매핑은 "/board/posts/*/likes"처럼 경로 중간에 오는 와일드카드를
	// 지원하지 않는다. 이런 엔드포인트는 넓은 prefix 패턴으로 등록하고, 실제로 제한을 적용할
	// 요청인지는 이 predicate로 걸러낸다(그 외 요청은 그대로 통과).
	public RateLimitFilter(ObjectMapper objectMapper, int maxRequestsPerWindow, Predicate<HttpServletRequest> requestMatcher) {
		this(objectMapper, maxRequestsPerWindow, requestMatcher, null);
	}

	public RateLimitFilter(
			ObjectMapper objectMapper, int maxRequestsPerWindow,
			Predicate<HttpServletRequest> requestMatcher, String bucketName) {
		this.objectMapper = objectMapper;
		this.maxRequestsPerWindow = maxRequestsPerWindow;
		this.requestMatcher = requestMatcher;
		this.bucketName = bucketName;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!requestMatcher.test(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		String clientKey = resolveClientKey(request) + "|" + (bucketName != null ? bucketName : request.getRequestURI());
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
