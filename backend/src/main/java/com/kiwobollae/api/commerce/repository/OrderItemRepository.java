package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	@EntityGraph(attributePaths = "product")
	List<OrderItem> findAllByOrderId(Long orderId);

	@EntityGraph(attributePaths = "product")
	List<OrderItem> findAllByOrderIdIn(List<Long> orderIds);
}
