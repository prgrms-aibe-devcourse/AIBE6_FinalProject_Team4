package com.kiwobollae.api.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3Client for journal image uploads. aws.s3.access-key/secret-key are empty by
 * default (see application.yaml) — when left empty, falls back to the SDK's
 * default credentials provider chain (env vars, ~/.aws/credentials, IAM role);
 * when filled in (e.g. via application-secret.yaml for local dev), uses those
 * directly. Same optional-secret convention as MailConfig/oauth.* client secrets.
 */
@Configuration
public class S3Config {

	@Value("${aws.s3.region}")
	private String region;

	@Value("${aws.s3.access-key}")
	private String accessKey;

	@Value("${aws.s3.secret-key}")
	private String secretKey;

	@Bean
	public S3Client s3Client() {
		AwsCredentialsProvider credentialsProvider = (accessKey != null && !accessKey.isBlank()
				&& secretKey != null && !secretKey.isBlank())
				? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
				: DefaultCredentialsProvider.create();

		return S3Client.builder()
				.region(Region.of(region))
				.credentialsProvider(credentialsProvider)
				.build();
	}
}
