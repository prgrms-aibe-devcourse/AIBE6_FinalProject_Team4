package com.kiwobollae.api.commerce.gacha.repository;

import com.kiwobollae.api.commerce.gacha.entity.UserCardShardWallet;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCardShardWalletRepository extends JpaRepository<UserCardShardWallet, Long> {

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO user_card_shard_wallets
            (user_id, balance, lifetime_earned, lifetime_spent, version, created_at, updated_at)
          VALUES (:userId, 0, 0, 0, 0, :now, :now)
          ON DUPLICATE KEY UPDATE user_id = user_id
          """,
      nativeQuery = true)
  int ensureWallet(@Param("userId") Long userId, @Param("now") LocalDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from UserCardShardWallet w where w.userId = :userId")
  Optional<UserCardShardWallet> findForUpdate(@Param("userId") Long userId);
}
