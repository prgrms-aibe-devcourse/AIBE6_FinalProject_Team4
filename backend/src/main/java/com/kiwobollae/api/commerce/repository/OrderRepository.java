package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.Order;
import com.kiwobollae.api.commerce.entity.enums.ConfirmedBy;
import com.kiwobollae.api.commerce.entity.enums.DeliveryStatus;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@Query(value = "select o from Order o where o.user.id = :userId",
			countQuery = "select count(o) from Order o where o.user.id = :userId")
	Page<Order> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

	@Query("select o from Order o where o.id = :id and o.user.id = :userId")
	Optional<Order> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	// PAID∧PREPARING일 때만 취소 허용. 배송이 시작됐거나 이미 취소/확정된 주문은 0건 반환.
	@Modifying
	@Query("update Order o set o.status = :cancelled, o.cancelledAt = :cancelledAt "
			+ "where o.id = :id and o.status = :paid and o.deliveryStatus = :preparing")
	int cancelIfMatches(
			@Param("id") Long id,
			@Param("cancelled") OrderStatus cancelled,
			@Param("paid") OrderStatus paid,
			@Param("preparing") DeliveryStatus preparing,
			@Param("cancelledAt") LocalDateTime cancelledAt
	);

	// PAID∧DELIVERED일 때만 구매 확정 허용. 확정 후에는 취소 불가(상태가 더 이상 PAID가 아님).
	@Modifying
	@Query("update Order o set o.status = :confirmed, o.confirmedAt = :confirmedAt, o.confirmedBy = :confirmedBy "
			+ "where o.id = :id and o.status = :paid and o.deliveryStatus = :delivered")
	int confirmIfMatches(
			@Param("id") Long id,
			@Param("confirmed") OrderStatus confirmed,
			@Param("paid") OrderStatus paid,
			@Param("delivered") DeliveryStatus delivered,
			@Param("confirmedAt") LocalDateTime confirmedAt,
			@Param("confirmedBy") ConfirmedBy confirmedBy
	);
}
