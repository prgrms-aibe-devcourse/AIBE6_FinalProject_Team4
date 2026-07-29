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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GoogleOAuthClient implements OAuthClient {

	private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);
	private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
	private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;
	private final RestClient restClient = RestClient.create();

	public GoogleOAuthClient(
			@Value("${app.oauth.google.client-id:}") String clientId,
			@Value("${app.oauth.google.client-secret:}") String clientSecret,
			@Value("${app.oauth.google.redirect-uri:}") String redirectUri) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.redirectUri = redirectUri;
	}

	@Override
	public AuthProvider getProvider() {
		return AuthProvider.GOOGLE;
	}

	@Override
	public OAuthUserInfo fetchUserInfo(String code, String state) {
		try {
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("code", code);
			form.add("client_id", clientId);
			form.add("client_secret", clientSecret);
			form.add("redirect_uri", redirectUri);
			form.add("grant_type", "authorization_code");

			TokenResponse token = restClient.post()
					.uri(TOKEN_URI)
					.body(form)
					.retrieve()
					.body(TokenResponse.class);

			UserInfoResponse userInfo = restClient.get()
					.uri(USERINFO_URI)
					.header("Authorization", "Bearer " + token.accessToken())
					.retrieve()
					.body(UserInfoResponse.class);

			if (userInfo == null || userInfo.email() == null) {
				throw new BusinessException(ErrorCode.AUTH_OAUTH_AUTHENTICATION_FAILED);
			}
			return new OAuthUserInfo(userInfo.sub(), userInfo.email(), userInfo.name());
		} catch (RestClientResponseException e) {
			log.warn("Google OAuth 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
			throw new BusinessException(ErrorCode.AUTH_OAUTH_AUTHENTICATION_FAILED);
		} catch (RestClientException e) {
			log.warn("Google OAuth 실패", e);
			throw new BusinessException(ErrorCode.AUTH_OAUTH_AUTHENTICATION_FAILED);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record UserInfoResponse(String sub, String email, String name) {
	}
}
