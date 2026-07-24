package com.kiwobollae.api.point.repository;

import com.kiwobollae.api.point.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
}
