package com.kiwobollae.api.commerce.gacha.repository;

import com.kiwobollae.api.commerce.gacha.entity.UserCardCosmetic;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaCosmeticType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCardCosmeticRepository extends JpaRepository<UserCardCosmetic, Long> {

  boolean existsByUser_IdAndCosmeticCode(Long userId, String cosmeticCode);

  @EntityGraph(attributePaths = "user")
  List<UserCardCosmetic> findAllByUser_Id(Long userId);

  List<UserCardCosmetic> findAllByUser_IdAndCosmeticType(
      Long userId, GachaCosmeticType cosmeticType);
}
