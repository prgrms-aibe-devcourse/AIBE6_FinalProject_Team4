package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {

	@EntityGraph(attributePaths = "exchangeProduct")
	List<Card> findAllByStatusOrderByCreatedAtDesc(ActiveStatus status);

	@EntityGraph(attributePaths = "exchangeProduct")
	Optional<Card> findByIdAndStatus(Long id, ActiveStatus status);
}
