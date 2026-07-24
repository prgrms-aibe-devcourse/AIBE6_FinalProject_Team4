package com.kiwobollae.api.notification.service;

import com.kiwobollae.api.notification.repository.NotificationRepository;
import com.kiwobollae.api.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationSettingRepository notificationSettingRepository;
}
