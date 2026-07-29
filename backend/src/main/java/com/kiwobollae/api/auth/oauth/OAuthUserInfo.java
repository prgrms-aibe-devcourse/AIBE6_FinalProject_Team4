package com.kiwobollae.api.auth.oauth;

/**
 * Normalized profile fetched from a provider after exchanging an authorization
 * code — {@code providerId} is that provider's own stable user id (never the
 * email), used together with the provider itself as the lookup key so a later
 * email change on the provider's side can't orphan the linked account.
 */
public record OAuthUserInfo(
		String providerId,
		String email,
		String nickname
) {
}
