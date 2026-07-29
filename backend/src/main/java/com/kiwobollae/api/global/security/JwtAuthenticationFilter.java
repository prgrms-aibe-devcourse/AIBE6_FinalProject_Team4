package com.kiwobollae.api.global.security;

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

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = resolveToken(request);

		if (token != null) {
			JwtTokenProvider.TokenStatus status = jwtTokenProvider.checkToken(token);
			if (status == JwtTokenProvider.TokenStatus.VALID) {
				Long userId = jwtTokenProvider.getUserId(token);
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
