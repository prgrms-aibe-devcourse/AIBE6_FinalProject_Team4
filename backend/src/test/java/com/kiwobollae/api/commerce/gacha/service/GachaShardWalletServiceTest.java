package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import com.kiwobollae.api.commerce.gacha.repository.UserCardShardWalletRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GachaShardWalletServiceTest {

  @Mock private UserCardShardWalletRepository walletRepository;

  @Test
  void returnsZeroWithoutCreatingOrLockingWalletOnRead() {
    GachaShardWalletService service = new GachaShardWalletService(walletRepository);
    when(walletRepository.findById(7L)).thenReturn(Optional.empty());

    var response = service.getWallet(7L);

    assertThat(response.balance()).isZero();
    assertThat(response.lifetimeEarned()).isZero();
    assertThat(response.lifetimeSpent()).isZero();
    verify(walletRepository, never()).ensureWallet(eq(7L), any(LocalDateTime.class));
    verify(walletRepository, never()).findForUpdate(7L);
  }

  @Test
  void readsExistingWalletWithoutWriteLock() {
    GachaShardWalletService service = new GachaShardWalletService(walletRepository);
    UserCardShardWallet wallet = new UserCardShardWallet(7L, null, 40L, 50L, 10L, 0L, null, null);
    when(walletRepository.findById(7L)).thenReturn(Optional.of(wallet));

    var response = service.getWallet(7L);

    assertThat(response.balance()).isEqualTo(40L);
    assertThat(response.lifetimeEarned()).isEqualTo(50L);
    assertThat(response.lifetimeSpent()).isEqualTo(10L);
    verify(walletRepository, never()).findForUpdate(7L);
  }

  @Test
  void createsAndLocksWalletOnlyForWriteFlow() {
    GachaShardWalletService service = new GachaShardWalletService(walletRepository);
    UserCardShardWallet wallet = new UserCardShardWallet(7L, null, 0L, 0L, 0L, 0L, null, null);
    when(walletRepository.findForUpdate(7L)).thenReturn(Optional.of(wallet));

    assertThat(service.getOrCreateForUpdate(7L)).isSameAs(wallet);

    verify(walletRepository).ensureWallet(eq(7L), any(LocalDateTime.class));
    verify(walletRepository).findForUpdate(7L);
  }

  @Test
  void locksExistingWalletWithoutStartingAnotherTransaction() {
    GachaShardWalletService service = new GachaShardWalletService(walletRepository);
    UserCardShardWallet wallet = new UserCardShardWallet(7L, null, 10L, 10L, 0L, 0L, null, null);
    when(walletRepository.findForUpdate(7L)).thenReturn(Optional.of(wallet));

    assertThat(service.getOrCreateForUpdate(7L)).isSameAs(wallet);

    verify(walletRepository).ensureWallet(eq(7L), any(LocalDateTime.class));
    verify(walletRepository).findForUpdate(7L);
  }
}
