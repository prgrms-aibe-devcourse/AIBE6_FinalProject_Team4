package com.kiwobollae.api.auth.service;

public interface EmailSender {

	/**
	 * @param htmlBody full HTML email body (see VerificationEmailTemplate for the
	 *                 verification-code layout) — implementations must send this as
	 *                 text/html, not escape it as plain text.
	 */
	void send(String to, String subject, String htmlBody);
}
