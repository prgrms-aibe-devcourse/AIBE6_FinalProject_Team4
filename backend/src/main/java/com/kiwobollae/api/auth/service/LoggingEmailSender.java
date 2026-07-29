package com.kiwobollae.api.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev-only fallback so signup works locally without real SMTP credentials —
 * logs the email body (including the verification code) instead of sending it.
 * Active whenever app.mail.enabled is unset or false; SmtpEmailSender takes over
 * once real SMTP credentials are configured (see application-secret.yaml).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

	@Override
	public void send(String to, String subject, String body) {
		log.info("[DEV MAIL] to={} subject={}\n{}", to, subject, body);
	}
}
