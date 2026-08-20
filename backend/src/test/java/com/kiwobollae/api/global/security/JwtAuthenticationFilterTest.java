package com.kiwobollae.api.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.cache.UserStatusCache;
import com.kiwobollae.api.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
			"test_secret_key_needs_to_be_at_least_32_bytes_long",
			3600000L,
			1209600000L
	);

	@Mock private UserRepository userRepository;
	@Mock private UserStatusCache userStatusCache;
	@Mock private FilterChain filterChain;

	private JwtAuthenticationFilter filter;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private HttpServletRequest requestWithToken(String token) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		given(request.getHeader("Authorization")).willReturn("Bearer " + token);
		return request;
	}

	@Test
	void authenticatesWhenTokenValidAndUserActive() throws Exception {
		filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, userStatusCache);
		String token = jwtTokenProvider.generateAccessToken(1L, "USER");
		given(userStatusCache.get(1L)).willReturn(Optional.of(UserStatus.ACTIVE));
		HttpServletRequest request = requestWithToken(token);
		HttpServletResponse response = mock(HttpServletResponse.class);

		filter.doFilter(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
		verify(filterChain).doFilter(request, response);
	}

	// 캐시가 비어 있으면(캐시 미스) DB에서 상태를 조회하고, 그 결과를 다시 캐시에 채워
	// 다음 요청부터는 DB를 타지 않도록 해야 한다.
	@Test
	void fallsBackToDbAndPopulatesCacheOnCacheMiss() throws Exception {
		filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, userStatusCache);
		String token = jwtTokenProvider.generateAccessToken(1L, "USER");
		given(userStatusCache.get(1L)).willReturn(Optional.empty());
		given(userRepository.findStatusById(1L)).willReturn(Optional.of(UserStatus.ACTIVE));
		HttpServletRequest request = requestWithToken(token);
		HttpServletResponse response = mock(HttpServletResponse.class);

		filter.doFilter(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		verify(userStatusCache).put(1L, UserStatus.ACTIVE);
		verify(filterChain).doFilter(request, response);
	}

	// 토큰 자체는 유효해도(서명/만료 문제 없음) 발급 이후 관리자가 계정을 정지시켰다면, 이 필터가
	// 매 요청마다 현재 상태를 다시 확인해 통과시키지 않아야 한다 — 액세스 토큰 만료를 기다릴
	// 필요 없이 즉시 막히는 것이 이 수정의 핵심이다.
	@Test
	void rejectsWhenTokenValidButUserNoLongerActive() throws Exception {
		filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, userStatusCache);
		String token = jwtTokenProvider.generateAccessToken(1L, "USER");
		given(userStatusCache.get(1L)).willReturn(Optional.of(UserStatus.SUSPENDED));
		HttpServletRequest request = requestWithToken(token);
		HttpServletResponse response = mock(HttpServletResponse.class);

		filter.doFilter(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(request).setAttribute(
				JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE, ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void skipsStatusCheckWhenNoTokenPresent() throws Exception {
		filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, userStatusCache);
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);

		filter.doFilter(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(userStatusCache, never()).get(any());
		verify(filterChain).doFilter(request, response);
	}
}
