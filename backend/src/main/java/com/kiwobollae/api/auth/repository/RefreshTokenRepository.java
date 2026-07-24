package com.kiwobollae.api.auth.repository;

import com.kiwobollae.api.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
