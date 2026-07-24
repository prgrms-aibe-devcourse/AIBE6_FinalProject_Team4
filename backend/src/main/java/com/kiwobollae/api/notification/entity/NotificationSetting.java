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
@Table(name = "notification_setting", indexes = {
		@Index(name = "idx_notification_setting_user_id_type", columnList = "user_id, type", unique = true)
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationSetting extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Column(nullable = false)
	private Boolean enabled;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}
