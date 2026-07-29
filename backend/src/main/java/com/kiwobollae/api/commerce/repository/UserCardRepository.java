package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.UserCard;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCardRepository extends JpaRepository<UserCard, Long> {

	List<UserCard> findAllByUser_IdAndCard_IdIn(Long userId, Collection<Long> cardIds);

	Optional<UserCard> findByUser_IdAndCard_Id(Long userId, Long cardId);

	@EntityGraph(attributePaths = {"card", "card.exchangeProduct"})
	List<UserCard> findAllByUser_IdAndCountGreaterThanOrderByIdDesc(Long userId, Integer count);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO user_cards (user_id, card_id, count)
			VALUES (:userId, :cardId, :quantity)
			ON DUPLICATE KEY UPDATE count = count + VALUES(count)
			""", nativeQuery = true)
	int incrementCount(
			@Param("userId") Long userId,
			@Param("cardId") Long cardId,
			@Param("quantity") Integer quantity
	);

	@Modifying
	@Query("update UserCard uc set uc.count = uc.count - :requiredCount "
			+ "where uc.user.id = :userId and uc.card.id = :cardId and uc.count >= :requiredCount")
	int decrementCountIfEnough(
			@Param("userId") Long userId,
			@Param("cardId") Long cardId,
			@Param("requiredCount") Integer requiredCount
	);
}
