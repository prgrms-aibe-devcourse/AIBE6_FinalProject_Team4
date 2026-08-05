package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.commerce.gacha.dto.GachaShardWalletResponse;
import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import com.kiwobollae.api.commerce.gacha.repository.UserCardShardWalletRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GachaShardWalletService {

  private final UserCardShardWalletRepository walletRepository;

  @Transactional(readOnly = true)
  public GachaShardWalletResponse getWallet(Long userId) {
    requireUser(userId);
    return walletRepository
        .findById(userId)
        .map(GachaShardWalletResponse::from)
        .orElseGet(GachaShardWalletResponse::empty);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  UserCardShardWallet getOrCreateForUpdate(Long userId) {
    walletRepository.ensureWallet(userId, LocalDateTime.now(KST));
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
