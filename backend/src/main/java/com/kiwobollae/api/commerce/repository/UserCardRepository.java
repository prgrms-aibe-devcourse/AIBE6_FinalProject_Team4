package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.UserCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCardRepository extends JpaRepository<UserCard, Long> {
}
