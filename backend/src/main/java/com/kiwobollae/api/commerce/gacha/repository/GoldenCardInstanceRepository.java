package com.kiwobollae.api.commerce.gacha.repository;

import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoldenCardInstanceRepository extends JpaRepository<GoldenCardInstance, Long> {

  @Query(
      """
			select coalesce(max(g.goldenOriginRank), 0)
			from GoldenCardInstance g
			where g.card.id = :cardId
			""")
  Long findMaxOriginRank(@Param("cardId") Long cardId);
}
