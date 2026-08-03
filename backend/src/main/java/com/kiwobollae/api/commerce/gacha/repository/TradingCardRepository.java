package com.kiwobollae.api.commerce.gacha.repository;

import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradingCardRepository extends JpaRepository<TradingCard, Long> {

  Optional<TradingCard> findByCode(String code);

  List<TradingCard> findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus status);

  List<TradingCard> findAllByStatusAndRarityOrderByDisplayOrderAsc(
      TradingCardStatus status, TradingCardRarity rarity);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from TradingCard c where c.id = :id")
  Optional<TradingCard> findByIdForUpdate(@Param("id") Long id);
}
