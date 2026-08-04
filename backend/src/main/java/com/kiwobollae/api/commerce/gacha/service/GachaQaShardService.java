package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.dto.GachaShardWalletResponse;
import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local QA helper. Delete this class after shard/cosmetic QA is complete. */
@Service
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.qa.gacha-shards", name = "enabled", havingValue = "true")
public class GachaQaShardService {

  static final long GRANT_AMOUNT = 100L;

  private final GachaShardWalletService walletService;

  @Transactional
  public GachaShardWalletResponse grant(Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
    UserCardShardWallet wallet = walletService.getOrCreateForUpdate(userId);
    wallet.earn(GRANT_AMOUNT);
    return GachaShardWalletResponse.from(wallet);
  }
}
