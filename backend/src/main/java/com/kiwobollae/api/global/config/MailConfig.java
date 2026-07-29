package com.kiwobollae.api.global.config;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Hand-rolled JavaMailSender instead of spring-boot-starter-mail's
 * spring.mail.* property autoconfiguration — every setting lives under
 * app.mail.* and is wired here explicitly. Only created when app.mail.enabled
 * is true; when it's false, SmtpEmailSender (which depends on this bean) is
 * also disabled and LoggingEmailSender is used instead, so nothing tries to
 * construct a mail sender without real SMTP settings to back it.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class MailConfig {

	@Value("${app.mail.host}")
	private String host;

	@Value("${app.mail.port}")
	private int port;

	@Value("${app.mail.username}")
	private String username;

	@Value("${app.mail.password}")
	private String password;

	@Value("${app.mail.smtp-auth:true}")
	private boolean smtpAuth;

	@Value("${app.mail.starttls:true}")
	private boolean starttls;

	@Bean
	public JavaMailSender javaMailSender() {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(host);
		sender.setPort(port);
		sender.setUsername(username);
		sender.setPassword(password);
		sender.setDefaultEncoding("UTF-8");

		Properties props = sender.getJavaMailProperties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.auth", smtpAuth);
		props.put("mail.smtp.starttls.enable", starttls);

		return sender;
	}
}
