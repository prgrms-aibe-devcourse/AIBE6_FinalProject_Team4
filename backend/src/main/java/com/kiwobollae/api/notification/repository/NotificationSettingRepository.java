package com.kiwobollae.api.notification.repository;

import com.kiwobollae.api.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
}
