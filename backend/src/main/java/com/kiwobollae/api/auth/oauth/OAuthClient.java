package com.kiwobollae.api.auth.oauth;

import com.kiwobollae.api.auth.entity.enums.AuthProvider;

/**
 * One implementation per social provider — exchanges the authorization code
 * the frontend obtained from the provider's consent screen for that
 * provider's user profile. Never receives or stores the provider's own
 * access/refresh tokens beyond the single call needed to fetch the profile.
 */
public interface OAuthClient {

	AuthProvider getProvider();

	/**
	 * @param code  the authorization code from the provider's redirect callback
	 * @param state optional CSRF state value some providers (Naver) require to
	 *              be echoed back in the token exchange; ignored by providers
	 *              that don't need it
	 */
	OAuthUserInfo fetchUserInfo(String code, String state);
}
