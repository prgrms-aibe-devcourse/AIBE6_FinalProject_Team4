package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.commerce.gacha.dto.GachaShardWalletResponse;
import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import org.junit.jupiter.api.Test;

class GachaQaShardServiceTest {

  @Test
  void grantsOneHundredShardsToLockedWallet() {
    GachaShardWalletService walletService = mock(GachaShardWalletService.class);
    UserCardShardWallet wallet = mock(UserCardShardWallet.class);
    when(walletService.getOrCreateForUpdate(1L)).thenReturn(wallet);
    when(wallet.getBalance()).thenReturn(120L);
    when(wallet.getLifetimeEarned()).thenReturn(120L);
    when(wallet.getLifetimeSpent()).thenReturn(0L);

    GachaShardWalletResponse response = new GachaQaShardService(walletService).grant(1L);

    verify(wallet).earn(100L);
    assertThat(response.balance()).isEqualTo(120L);
    assertThat(response.lifetimeEarned()).isEqualTo(120L);
  }
}
