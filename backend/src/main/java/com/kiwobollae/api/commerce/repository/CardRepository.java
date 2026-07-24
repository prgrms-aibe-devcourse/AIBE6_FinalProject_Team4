package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {
}
