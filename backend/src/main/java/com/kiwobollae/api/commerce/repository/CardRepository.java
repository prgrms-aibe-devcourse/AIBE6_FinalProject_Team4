package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, Long> {

	@EntityGraph(attributePaths = "exchangeProduct")
	List<Card> findAllByStatusOrderByCreatedAtDesc(ActiveStatus status);

	@EntityGraph(attributePaths = "exchangeProduct")
	Optional<Card> findByIdAndStatus(Long id, ActiveStatus status);

	@EntityGraph(attributePaths = "exchangeProduct")
	List<Card> findAllByOrderByCreatedAtDesc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "exchangeProduct")
	@Query("select c from Card c where c.id = :id")
	Optional<Card> findByIdForUpdate(@Param("id") Long id);
}
