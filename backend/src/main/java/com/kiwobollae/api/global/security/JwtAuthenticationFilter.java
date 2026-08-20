package com.kiwobollae.api.global.security;

import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.cache.UserStatusCache;
import com.kiwobollae.api.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the Bearer access token on every request and, if valid, populates the
 * SecurityContext so the authenticated user is available application-wide
 * (e.g. via {@code @AuthenticationPrincipal Long userId}).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String HEADER = "Authorization";
	private static final String PREFIX = "Bearer ";

	/** Read by JwtAuthenticationEntryPoint to report *why* auth failed, not just that it did. */
	public static final String AUTH_ERROR_CODE_ATTRIBUTE = "authErrorCode";

	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;
	private final UserStatusCache userStatusCache;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = resolveToken(request);

		if (token != null) {
			JwtTokenProvider.TokenStatus status = jwtTokenProvider.checkToken(token);
			if (status == JwtTokenProvider.TokenStatus.VALID && jwtTokenProvider.isAccessToken(token)) {
				Long userId = jwtTokenProvider.getUserId(token);
				// JWT의 서명/만료만 검증하고 role 클레임을 그대로 믿으면, 발급 이후(관리자가 계정을
				// 정지시키는 등) 상태가 바뀌어도 액세스 토큰이 만료되기 전까지(최대 1시간) 그대로
				// 통과한다. 모든 요청이 지나가는 이 필터 한 곳에서만 현재 상태를 다시 확인하면,
				// 기능별 컨트롤러/서비스를 하나씩 고칠 필요 없이 전체가 즉시 막힌다.
				// 이 재확인이 매 요청 DB 왕복을 추가하므로 UserStatusCache(Redis, TTL 30초)를
				// 먼저 보고, 미스일 때만 DB를 친 뒤 캐시를 채운다 — 정지/해제 시점에 캐시가
				// evict되므로 "즉시 반영"이라는 성질은 그대로 유지된다.
				UserStatus userStatus = userStatusCache.get(userId).orElseGet(() -> {
					UserStatus fetched = userRepository.findStatusById(userId).orElse(null);
					if (fetched != null) {
						userStatusCache.put(userId, fetched);
					}
					return fetched;
				});
				if (userStatus != UserStatus.ACTIVE) {
					request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
					filterChain.doFilter(request, response);
					return;
				}

				String role = jwtTokenProvider.getRole(token);
				List<GrantedAuthority> authorities = role != null
						? List.of(new SimpleGrantedAuthority("ROLE_" + role))
						: List.of();

				var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} else {
				ErrorCode errorCode = status == JwtTokenProvider.TokenStatus.EXPIRED
						? ErrorCode.AUTH_TOKEN_EXPIRED
						: ErrorCode.AUTH_TOKEN_INVALID;
				request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, errorCode);
			}
		}

		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HEADER);
		if (header != null && header.startsWith(PREFIX)) {
			return header.substring(PREFIX.length());
		}
		return null;
	}
}
