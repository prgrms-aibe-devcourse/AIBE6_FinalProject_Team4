package com.kiwobollae.api.ai.config;

import com.kiwobollae.api.ai.guide.PlantCareGuideProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  OpenAiProperties.class,
  AiPolicyProperties.class,
  AiImageProperties.class,
  PlantCareGuideProperties.class
})
public class AiConfig {}
