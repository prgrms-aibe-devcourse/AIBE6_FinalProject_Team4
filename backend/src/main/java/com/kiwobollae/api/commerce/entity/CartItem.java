package com.kiwobollae.api.commerce.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cart_items", indexes = {
		@Index(name = "idx_cart_items_user_id_product_id", columnList = "user_id, product_id", unique = true)
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CartItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private Integer quantity;

	// 동시에 같은 항목에 담기/수량변경이 들어와 lost update가 나지 않도록 낙관적 락을 건다
	// (정책 #10: 일반 엔티티 갱신은 @Version 기본).
	@Version
	@Column(nullable = false)
	private Long version;

	public void changeQuantity(Integer quantity) {
		this.quantity = quantity;
	}
}
