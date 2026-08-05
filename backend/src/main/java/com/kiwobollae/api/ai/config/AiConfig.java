package com.kiwobollae.api.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  OpenAiProperties.class,
  AiPolicyProperties.class,
  AiImageProperties.class
})
public class AiConfig {}
