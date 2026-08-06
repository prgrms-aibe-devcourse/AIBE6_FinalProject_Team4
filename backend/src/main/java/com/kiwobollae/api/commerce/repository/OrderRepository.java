package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.Order;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
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

	// 관리자용 전체 목록 조회. 각 필터는 null이면 조건에서 제외된다.
	// join fetch로 User를 함께 가져오지 않으면 OrderResponse.from(order)가 각 건마다
	// order.getUser()를 lazy-load해 페이지 크기만큼 N+1 쿼리가 나간다.
	@Query(value = "select o from Order o join fetch o.user where (:status is null or o.status = :status) "
			+ "and (:deliveryStatus is null or o.deliveryStatus = :deliveryStatus) "
			+ "and (:userId is null or o.user.id = :userId) "
			+ "and (:from is null or o.orderedAt >= :from) "
			+ "and (:to is null or o.orderedAt <= :to)",
			countQuery = "select count(o) from Order o where (:status is null or o.status = :status) "
					+ "and (:deliveryStatus is null or o.deliveryStatus = :deliveryStatus) "
					+ "and (:userId is null or o.user.id = :userId) "
					+ "and (:from is null or o.orderedAt >= :from) "
					+ "and (:to is null or o.orderedAt <= :to)")
	Page<Order> search(
			@Param("status") OrderStatus status,
			@Param("deliveryStatus") DeliveryStatus deliveryStatus,
			@Param("userId") Long userId,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			Pageable pageable
	);

	// PAID 상태에서만 배송 상태를 전이한다. 취소/확정된 주문은 status 조건에서 걸러진다.
	@Modifying
	@Query("update Order o set o.deliveryStatus = :newStatus "
			+ "where o.id = :id and o.status = :paid and o.deliveryStatus = :expectedStatus")
	int updateDeliveryStatusIfMatches(
			@Param("id") Long id,
			@Param("newStatus") DeliveryStatus newStatus,
			@Param("paid") OrderStatus paid,
			@Param("expectedStatus") DeliveryStatus expectedStatus
	);

	@Modifying
	@Query("update Order o set o.deliveryStatus = :delivered, o.deliveredAt = :deliveredAt "
			+ "where o.id = :id and o.status = :paid and o.deliveryStatus = :expectedStatus")
	int deliverIfMatches(
			@Param("id") Long id,
			@Param("delivered") DeliveryStatus delivered,
			@Param("deliveredAt") LocalDateTime deliveredAt,
			@Param("paid") OrderStatus paid,
			@Param("expectedStatus") DeliveryStatus expectedStatus
	);

	// PAID∧PREPARING일 때만 취소 허용. 배송이 시작됐거나 이미 취소/확정된 주문은 0건 반환.
	// cancelReason/cancelledBy는 사용자 본인 취소 시 각각 null/USER로 넘어온다.
	// clearAutomatically 없이는 호출 전에 로드해둔 Order가 영속성 컨텍스트에 PAID로 캐시된 채 남아,
	// 이후 같은 id를 재조회해도 갱신 전 상태를 그대로 반환한다(OrderManagementService.adminCancelOrder).
	@Modifying(clearAutomatically = true)
	@Query("update Order o set o.status = :cancelled, o.cancelledAt = :cancelledAt, "
			+ "o.cancelReason = :cancelReason, o.cancelledBy = :cancelledBy "
			+ "where o.id = :id and o.status = :paid and o.deliveryStatus = :preparing")
	int cancelIfMatches(
			@Param("id") Long id,
			@Param("cancelled") OrderStatus cancelled,
			@Param("paid") OrderStatus paid,
			@Param("preparing") DeliveryStatus preparing,
			@Param("cancelledAt") LocalDateTime cancelledAt,
			@Param("cancelReason") String cancelReason,
			@Param("cancelledBy") CancelledBy cancelledBy
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
