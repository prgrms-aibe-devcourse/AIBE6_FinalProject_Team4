package com.kiwobollae.api.notification.dto.response;

import com.kiwobollae.api.notification.entity.Notification;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
		Long id,
		Long userId,
		NotificationType type,
		String title,
		String content,
		String linkUrl,
		String refType,
		Long refId,
		Boolean isRead,
		LocalDateTime readAt,
		LocalDateTime createdAt
) {
	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getUser().getId(),
				notification.getType(),
				notification.getTitle(),
				notification.getContent(),
				notification.getLinkUrl(),
				notification.getRefType(),
				notification.getRefId(),
				notification.getIsRead(),
				notification.getReadAt(),
				notification.getCreatedAt()
		);
	}
}
