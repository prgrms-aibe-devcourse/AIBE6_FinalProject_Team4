package com.kiwobollae.api.commerce.gacha.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureGachaRandomSource implements GachaRandomSource {

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public int nextInt(int bound) {
    return secureRandom.nextInt(bound);
  }
}
