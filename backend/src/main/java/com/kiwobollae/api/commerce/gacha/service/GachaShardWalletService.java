package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.dto.GachaShardWalletResponse;
import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import com.kiwobollae.api.commerce.gacha.repository.UserCardShardWalletRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GachaShardWalletService {

  private final UserCardShardWalletRepository walletRepository;
  private final GachaShardWalletInitializer walletInitializer;

  @Transactional
  public GachaShardWalletResponse getWallet(Long userId) {
    requireUser(userId);
    return GachaShardWalletResponse.from(getOrCreateForUpdate(userId));
  }

  UserCardShardWallet getOrCreateForUpdate(Long userId) {
    walletInitializer.ensure(userId);
    return walletRepository
        .findForUpdate(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR));
  }

  private void requireUser(Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
  }
}
