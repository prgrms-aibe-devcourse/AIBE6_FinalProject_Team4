package com.kiwobollae.api.commerce.gacha.repository;

import com.kiwobollae.api.commerce.gacha.entity.GachaDrawItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GachaDrawItemRepository extends JpaRepository<GachaDrawItem, Long> {

  @EntityGraph(attributePaths = {"card", "goldenInstance"})
  List<GachaDrawItem> findAllByGachaDraw_IdOrderByDrawSeqAsc(Long drawId);
}
