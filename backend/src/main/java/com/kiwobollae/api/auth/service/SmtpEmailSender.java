package com.kiwobollae.api.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender javaMailSender;

	@Value("${app.mail.from}")
	private String from;

	@Override
	public void send(String to, String subject, String htmlBody) {
		MimeMessage message = javaMailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(htmlBody, true);
		} catch (MessagingException e) {
			throw new MailSendException("이메일 메시지를 구성하는 중 오류가 발생했습니다.", e);
		}
		javaMailSender.send(message);
	}
}
