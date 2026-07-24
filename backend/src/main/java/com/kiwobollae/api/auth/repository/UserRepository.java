package com.kiwobollae.api.auth.repository;

import com.kiwobollae.api.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);
}
