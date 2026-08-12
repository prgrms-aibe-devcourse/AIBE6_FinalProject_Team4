package com.kiwobollae.api.commerce.gacha.repository;

import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from GoldenCardInstance g where g.id = :id")
  Optional<GoldenCardInstance> findByIdForUpdate(@Param("id") Long id);

  List<GoldenCardInstance> findAllByOwnerUser_IdOrderByIdAsc(Long ownerUserId);
}
