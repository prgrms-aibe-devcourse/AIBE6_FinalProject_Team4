package com.kiwobollae.api.commerce.gacha.entity;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_card_shard_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserCardShardWallet {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private Long balance = 0L;

  @Column(name = "lifetime_earned", nullable = false)
  private Long lifetimeEarned = 0L;

  @Column(name = "lifetime_spent", nullable = false)
  private Long lifetimeSpent = 0L;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public void earn(long amount) {
    balance = Math.addExact(balance, amount);
    lifetimeEarned = Math.addExact(lifetimeEarned, amount);
  }

  public boolean spend(long amount) {
    if (amount < 1 || balance < amount) {
      return false;
    }
    balance -= amount;
    lifetimeSpent = Math.addExact(lifetimeSpent, amount);
    return true;
  }

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now(KST);
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now(KST);
  }
}
