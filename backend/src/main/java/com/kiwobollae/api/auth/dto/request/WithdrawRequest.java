package com.kiwobollae.api.auth.dto.request;

/**
 * password is required only for LOCAL accounts (social-login accounts have no
 * password to check — the valid access token itself is the confirmation).
 */
public record WithdrawRequest(
		String password
) {
}
