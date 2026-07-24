package com.kiwobollae.api.notification.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification", indexes = {
		@Index(name = "idx_notification_user_id_created_at", columnList = "user_id, create_at"),
		@Index(name = "idx_notification_user_id_is_read", columnList = "user_id, is_read"),
		@Index(name = "idx_notification_user_type_ref", columnList = "user_id, type, ref_type, ref_id"),
		@Index(name = "idx_notification_created_at", columnList = "create_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 500)
	private String content;

	@Column(name = "link_url", length = 500)
	private String linkUrl;

	@Column(name = "ref_type", length = 30)
	private String refType;

	/** Polymorphic reference — no FK mapping, target table determined by refType. */
	@Column(name = "ref_id")
	private Long refId;

	@Column(name = "is_read", nullable = false)
	private Boolean isRead;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "create_at", nullable = false)
	private LocalDateTime createdAt;
}
