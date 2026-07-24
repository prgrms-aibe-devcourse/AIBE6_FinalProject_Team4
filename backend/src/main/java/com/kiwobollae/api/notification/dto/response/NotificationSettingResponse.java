package com.kiwobollae.api.notification.dto.response;

import com.kiwobollae.api.notification.entity.NotificationSetting;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationSettingResponse(
		Long id,
		Long userId,
		NotificationType type,
		Boolean enabled,
		LocalDateTime updatedAt
) {
	public static NotificationSettingResponse from(NotificationSetting notificationSetting) {
		return new NotificationSettingResponse(
				notificationSetting.getId(),
				notificationSetting.getUser().getId(),
				notificationSetting.getType(),
				notificationSetting.getEnabled(),
				notificationSetting.getUpdatedAt()
		);
	}
}
