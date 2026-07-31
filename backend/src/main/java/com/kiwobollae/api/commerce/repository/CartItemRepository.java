package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.CartItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

	@EntityGraph(attributePaths = "product")
	List<CartItem> findAllByUserIdOrderByIdDesc(Long userId);

	@EntityGraph(attributePaths = "product")
	List<CartItem> findAllByIdInAndUserId(List<Long> ids, Long userId);

	Optional<CartItem> findByIdAndUserId(Long id, Long userId);

	Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

	// 주문 생성 시 같은 항목으로 서로 다른 멱등키를 쓴 동시 결제 요청이 같은 카트 항목을 두 번
	// 소비하지 못하도록 행 잠금을 건다. 먼저 잠근 트랜잭션이 커밋(삭제)될 때까지 나머지는 대기하고,
	// 이후 조회하면 이미 삭제되어 개수가 줄어들어 CART_ITEM_NOT_FOUND로 안전하게 막힌다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select ci from CartItem ci join fetch ci.product where ci.id in :ids and ci.user.id = :userId")
	List<CartItem> findAllByIdInAndUserIdForUpdate(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
