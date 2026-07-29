package com.kiwobollae.api.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NaverOAuthClient implements OAuthClient {

	private static final Logger log = LoggerFactory.getLogger(NaverOAuthClient.class);
	private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
	private static final String USERINFO_URI = "https://openapi.naver.com/v1/nid/me";

	private final String clientId;
	private final String clientSecret;
	private final RestClient restClient = RestClient.create();

	public NaverOAuthClient(
			@Value("${app.oauth.naver.client-id:}") String clientId,
			@Value("${app.oauth.naver.client-secret:}") String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public AuthProvider getProvider() {
		return AuthProvider.NAVER;
	}

	@Override
	public OAuthUserInfo fetchUserInfo(String code, String state) {
		try {
			// 네이버는 state를 프론트가 인가 요청 때 생성해 넘긴 값 그대로 토큰 교환에도 실어 보내야 한다
			// (state 검증 자체는 프론트가 원래 값을 기억했다가 콜백에서 대조하는 몫).
			String tokenUri = UriComponentsBuilder.fromUriString(TOKEN_URI)
					.queryParam("grant_type", "authorization_code")
					.queryParam("client_id", clientId)
					.queryParam("client_secret", clientSecret)
					.queryParam("code", code)
					.queryParam("state", state)
					.build()
					.toUriString();

			TokenResponse token = restClient.get()
					.uri(tokenUri)
					.retrieve()
					.body(TokenResponse.class);

			UserInfoResponse userInfo = restClient.get()
					.uri(USERINFO_URI)
					.header("Authorization", "Bearer " + token.accessToken())
					.retrieve()
					.body(UserInfoResponse.class);

			if (userInfo == null || userInfo.response() == null || userInfo.response().email() == null) {
				throw new BusinessException(ErrorCode.AUTH_OAUTH_AUTHENTICATION_FAILED);
			}
			return new OAuthUserInfo(
					userInfo.response().id(), userInfo.response().email(), userInfo.response().name());
		} catch (RestClientResponseException e) {
			log.warn("Naver OAuth 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
			throw new BusinessException(ErrorCode.AUTH_OAUTH_AUTHENTICATION_FAILED);
		} catch (RestClientException e) {
			log.warn("Naver OAuth 실패", e);
			throw new BusinessException(ErrorCode.AUTH_OAUTH_AUTHENTICATION_FAILED);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record UserInfoResponse(NaverProfile response) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record NaverProfile(String id, String email, String name) {
	}
}
