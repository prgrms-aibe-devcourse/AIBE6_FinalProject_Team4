package com.kiwobollae.api.commerce.gacha.repository;

import com.kiwobollae.api.commerce.gacha.entity.UserCardCollection;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCardCollectionRepository extends JpaRepository<UserCardCollection, Long> {

  @EntityGraph(attributePaths = "card")
  List<UserCardCollection> findAllByUser_Id(Long userId);

  @EntityGraph(attributePaths = "card")
  List<UserCardCollection> findAllByUser_IdAndCard_IdIn(Long userId, Collection<Long> cardIds);

  Optional<UserCardCollection> findByUser_IdAndCard_Id(Long userId, Long cardId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
			select c
			from UserCardCollection c
			where c.user.id = :userId and c.card.id = :cardId
			""")
  Optional<UserCardCollection> findForUpdate(
      @Param("userId") Long userId, @Param("cardId") Long cardId);

  @Query(
      """
			select c.card.id
			from UserCardCollection c
			where c.user.id = :userId
			  and c.card.rarity = :rarity
			  and c.goldenGachaAcquiredAt is not null
			""")
  List<Long> findGachaAcquiredCardIds(
      @Param("userId") Long userId, @Param("rarity") TradingCardRarity rarity);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
			INSERT IGNORE INTO user_card_collections
				(user_id, card_id, owned_count, first_acquired_at, golden_gacha_acquired_at, updated_at)
			VALUES (:userId, :cardId, 0, :now, NULL, :now)
			""",
      nativeQuery = true)
  int ensureCollectionRow(
      @Param("userId") Long userId, @Param("cardId") Long cardId, @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
			INSERT INTO user_card_collections
				(user_id, card_id, owned_count, first_acquired_at, golden_gacha_acquired_at, updated_at)
			VALUES (:userId, :cardId, 1, :now, NULL, :now)
			ON DUPLICATE KEY UPDATE
				owned_count = owned_count + 1,
				updated_at = VALUES(updated_at)
			""",
      nativeQuery = true)
  int incrementOwnedCount(
      @Param("userId") Long userId, @Param("cardId") Long cardId, @Param("now") LocalDateTime now);

  @Query(
      value =
          """
			SELECT owned_count
			FROM user_card_collections
			WHERE user_id = :userId AND card_id = :cardId
			""",
      nativeQuery = true)
  Optional<Integer> findOwnedCount(@Param("userId") Long userId, @Param("cardId") Long cardId);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
			UPDATE user_card_collections
			SET owned_count = owned_count - :quantity,
				updated_at = :now
			WHERE user_id = :userId
			  AND card_id = :cardId
			  AND owned_count >= :quantity + 1
			""",
      nativeQuery = true)
  int decrementKeepingOne(
      @Param("userId") Long userId,
      @Param("cardId") Long cardId,
      @Param("quantity") int quantity,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
			UPDATE user_card_collections
			SET owned_count = owned_count - 1,
				updated_at = :now
			WHERE user_id = :userId
			  AND card_id = :cardId
			  AND owned_count >= 1
			""",
      nativeQuery = true)
  int decrementOwnedCount(
      @Param("userId") Long userId, @Param("cardId") Long cardId, @Param("now") LocalDateTime now);
}
