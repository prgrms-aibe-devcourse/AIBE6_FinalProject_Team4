package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeProductRepository extends JpaRepository<ExchangeProduct, Long> {

	List<ExchangeProduct> findAllByStatusOrderByCreatedAtDesc(ActiveStatus status);

	Optional<ExchangeProduct> findByIdAndStatus(Long id, ActiveStatus status);

	@Modifying
	@Query("update ExchangeProduct p set p.stock = p.stock - 1 where p.id = :id and p.stock >= 1")
	int decrementStockIfAvailable(@Param("id") Long id);

	@Modifying
	@Query("update ExchangeProduct p set p.stock = p.stock + 1 where p.id = :id")
	int incrementStock(@Param("id") Long id);
}
