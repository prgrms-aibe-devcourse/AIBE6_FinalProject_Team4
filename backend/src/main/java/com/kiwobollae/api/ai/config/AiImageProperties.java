package com.kiwobollae.api.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.image")
public record AiImageProperties(String publicBaseUrl) {}
