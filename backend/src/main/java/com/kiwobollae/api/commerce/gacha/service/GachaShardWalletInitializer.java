package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.commerce.gacha.repository.UserCardShardWalletRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GachaShardWalletInitializer {

  private final UserCardShardWalletRepository walletRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void ensure(Long userId) {
    walletRepository.ensureWallet(userId, LocalDateTime.now(KST));
  }
}
