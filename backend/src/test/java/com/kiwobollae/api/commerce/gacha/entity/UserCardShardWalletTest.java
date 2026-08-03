package com.kiwobollae.api.commerce.gacha.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserCardShardWalletTest {

  @Test
  void tracksEarnedAndSpentShardsSeparately() {
    UserCardShardWallet wallet = new UserCardShardWallet(1L, null, 0L, 0L, 0L, 0L, null, null);

    wallet.earn(30);
    boolean spent = wallet.spend(20);

    assertThat(spent).isTrue();
    assertThat(wallet.getBalance()).isEqualTo(10);
    assertThat(wallet.getLifetimeEarned()).isEqualTo(30);
    assertThat(wallet.getLifetimeSpent()).isEqualTo(20);
  }

  @Test
  void refusesSpendingMoreThanBalance() {
    UserCardShardWallet wallet = new UserCardShardWallet(1L, null, 5L, 5L, 0L, 0L, null, null);

    assertThat(wallet.spend(6)).isFalse();
    assertThat(wallet.getBalance()).isEqualTo(5);
    assertThat(wallet.getLifetimeSpent()).isZero();
  }
}
