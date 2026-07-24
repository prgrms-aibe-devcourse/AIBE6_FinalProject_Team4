package com.kiwobollae.api.point.repository;

import com.kiwobollae.api.point.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

	@Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
	Optional<Wallet> findByUserId(@Param("userId") Long userId);

	/** 포인트 증감 시 동시성 제어용 비관적 락(정책 #10: wallet은 version 없이 행 잠금). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
	Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);
}
