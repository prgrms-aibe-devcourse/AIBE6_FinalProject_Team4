package com.kiwobollae.api.global.config;

import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.security.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {

	@Bean
	public FilterRegistrationBean<RateLimitFilter> reissueRateLimitFilter(ObjectMapper objectMapper) {
		FilterRegistrationBean<RateLimitFilter> registration =
				new FilterRegistrationBean<>(new RateLimitFilter(objectMapper, 30));
		registration.addUrlPatterns(ApiVersion.V1 + "/auth/reissue");
		registration.setName("reissueRateLimitFilter");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> emailVerificationRateLimitFilter(ObjectMapper objectMapper) {
		// Much stricter than reissue: sending email costs real money/reputation and is a
		// common enumeration/spam vector, so cap it well below the generic default.
		FilterRegistrationBean<RateLimitFilter> registration =
				new FilterRegistrationBean<>(new RateLimitFilter(objectMapper, 5));
		registration.addUrlPatterns(ApiVersion.V1 + "/auth/signup/email-verification");
		registration.setName("emailVerificationRateLimitFilter");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> passwordVerifyRateLimitFilter(ObjectMapper objectMapper) {
		// Guards against brute-forcing the current password via repeated verify/change attempts.
		FilterRegistrationBean<RateLimitFilter> registration =
				new FilterRegistrationBean<>(new RateLimitFilter(objectMapper, 5));
		registration.addUrlPatterns(
				ApiVersion.V1 + "/auth/me/password/verify",
				ApiVersion.V1 + "/auth/me/password");
		registration.setName("passwordVerifyRateLimitFilter");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> loginRateLimitFilter(ObjectMapper objectMapper) {
		// Login is unauthenticated by definition, making it the single highest-value
		// target for password brute-forcing — must be capped independently of the
		// authenticated-endpoint filter above.
		FilterRegistrationBean<RateLimitFilter> registration =
				new FilterRegistrationBean<>(new RateLimitFilter(objectMapper, 10));
		registration.addUrlPatterns(ApiVersion.V1 + "/auth/login");
		registration.setName("loginRateLimitFilter");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> passwordResetRateLimitFilter(ObjectMapper objectMapper) {
		// The whole password-reset trio is also unauthenticated (/auth/** permitAll),
		// so it's just as brute-forceable/spammable as login and signup verification.
		FilterRegistrationBean<RateLimitFilter> registration =
				new FilterRegistrationBean<>(new RateLimitFilter(objectMapper, 5));
		registration.addUrlPatterns(
				ApiVersion.V1 + "/auth/password/reset/email-verification",
				ApiVersion.V1 + "/auth/password/reset/email-verification/confirm",
				ApiVersion.V1 + "/auth/password/reset");
		registration.setName("passwordResetRateLimitFilter");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> boardLikeRateLimitFilter(ObjectMapper objectMapper) {
		// 좋아요/좋아요 취소를 스팸 클릭(또는 UI를 우회한 스크립트 반복 호출)해도 DB에 부담을
		// 주지 않도록 게시글/댓글 좋아요 엔드포인트에 분당 10회 제한을 건다. urlPatterns가
		// "/board/posts/*/likes" 같은 중간 와일드카드를 지원하지 않아 넓은 prefix로 등록하고,
		// 실제 필터링 대상은 requestMatcher로 "/likes"로 끝나는 요청만 걸러낸다.
		FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
				new RateLimitFilter(objectMapper, 10, request -> request.getRequestURI().endsWith("/likes")));
		registration.addUrlPatterns(ApiVersion.V1 + "/board/*");
		registration.setName("boardLikeRateLimitFilter");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> boardPostCreateRateLimitFilter(ObjectMapper objectMapper) {
		// 게시글 도배 방지: 짧은 시간에 여러 글을 찍어내는 스팸/스크립트성 작성을 막기 위해
		// 분당 5회로 제한한다. urlPatterns가 "/board/posts"에 정확히 매핑되므로 목록 조회(GET)도
		// 함께 걸리지만, requestMatcher로 POST(작성)만 실제 집계 대상으로 걸러낸다.
		FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
				new RateLimitFilter(objectMapper, 5, request -> "POST".equalsIgnoreCase(request.getMethod())));
		registration.addUrlPatterns(ApiVersion.V1 + "/board/posts");
		registration.setName("boardPostCreateRateLimitFilter");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> boardCommentCreateRateLimitFilter(ObjectMapper objectMapper) {
		// 댓글 도배 방지: 분당 10회로 제한한다. urlPatterns가 "/board/posts/*/comments"처럼 중간
		// 와일드카드를 지원하지 않아 넓은 prefix로 등록하고, "게시글 id/comments"로 끝나는 POST만
		// requestMatcher로 걸러낸다. bucketName을 고정값으로 줘서 게시글 id별로 버킷이 갈라지지
		// 않게 한다 — 안 그러면 여러 글에 나눠서 도배해도 각 글마다 새 예산으로 통과해버린다.
		FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(
				objectMapper, 10,
				request -> "POST".equalsIgnoreCase(request.getMethod())
						&& request.getRequestURI().matches(".*/board/posts/\\d+/comments$"),
				"board-comment-create"));
		registration.addUrlPatterns(ApiVersion.V1 + "/board/*");
		registration.setName("boardCommentCreateRateLimitFilter");
		registration.setOrder(1);
		return registration;
	}
}
