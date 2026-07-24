package com.kiwobollae.api.mypage.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_address", indexes = {
		@Index(name = "idx_user_address_user_id_is_default", columnList = "user_id, is_default")
})
@AttributeOverrides({
		@AttributeOverride(name = "createdAt", column = @Column(name = "create_at", nullable = false, updatable = false)),
		@AttributeOverride(name = "updatedAt", column = @Column(name = "update_at", nullable = false))
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAddress extends BaseTimeEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "receiver_name", nullable = false, length = 50)
	private String receiverName;

	@Column(name = "receiver_phone", nullable = false, length = 20)
	private String receiverPhone;

	@Column(name = "zip_code", nullable = false, length = 10)
	private String zipCode;

	@Column(nullable = false, length = 200)
	private String address;

	@Column(name = "address_detail", length = 100)
	private String addressDetail;

	@Column(name = "is_default", nullable = false)
	private Boolean isDefault;
}
