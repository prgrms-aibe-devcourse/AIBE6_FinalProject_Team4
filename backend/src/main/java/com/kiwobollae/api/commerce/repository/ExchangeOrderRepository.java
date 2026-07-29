package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrder, Long> {

	@Query(value = "select eo from ExchangeOrder eo where eo.user.id = :userId",
			countQuery = "select count(eo) from ExchangeOrder eo where eo.user.id = :userId")
	Page<ExchangeOrder> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

	@Query("select eo from ExchangeOrder eo where eo.id = :id and eo.user.id = :userId")
	Optional<ExchangeOrder> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	@Query(value = "select eo from ExchangeOrder eo where (:status is null or eo.status = :status)",
			countQuery = "select count(eo) from ExchangeOrder eo where (:status is null or eo.status = :status)")
	Page<ExchangeOrder> search(@Param("status") ExchangeStatus status, Pageable pageable);

	@Modifying
	@Query("update ExchangeOrder eo set eo.status = :newStatus "
			+ "where eo.id = :id and eo.status = :expectedStatus")
	int updateStatusIfMatches(@Param("id") Long id, @Param("newStatus") ExchangeStatus newStatus,
			@Param("expectedStatus") ExchangeStatus expectedStatus);

	@Modifying
	@Query("update ExchangeOrder eo set eo.status = com.kiwobollae.api.commerce.entity.enums.ExchangeStatus.DELIVERED, "
			+ "eo.deliveredAt = :deliveredAt where eo.id = :id and eo.status = :expectedStatus")
	int deliverIfMatches(@Param("id") Long id, @Param("deliveredAt") LocalDateTime deliveredAt,
			@Param("expectedStatus") ExchangeStatus expectedStatus);

	@Modifying
	@Query("update ExchangeOrder eo set eo.status = com.kiwobollae.api.commerce.entity.enums.ExchangeStatus.CANCELLED, "
			+ "eo.cancelledBy = :cancelledBy, eo.cancelReason = :cancelReason, eo.cancelledAt = :cancelledAt "
			+ "where eo.id = :id and eo.status = :expectedStatus")
	int cancelIfMatches(@Param("id") Long id, @Param("cancelledBy") CancelledBy cancelledBy,
			@Param("cancelReason") String cancelReason, @Param("cancelledAt") LocalDateTime cancelledAt,
			@Param("expectedStatus") ExchangeStatus expectedStatus);
}
