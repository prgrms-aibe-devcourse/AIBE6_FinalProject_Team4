package com.kiwobollae.api.notification.dto.request;

import com.kiwobollae.api.notification.entity.enums.NotificationType;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingRequest(
		@NotNull NotificationType type,
		@NotNull Boolean enabled
) {
}
