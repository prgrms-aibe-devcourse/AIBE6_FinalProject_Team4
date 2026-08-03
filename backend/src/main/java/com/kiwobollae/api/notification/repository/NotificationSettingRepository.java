package com.kiwobollae.api.notification.repository;

import com.kiwobollae.api.notification.entity.NotificationSetting;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

	List<NotificationSetting> findAllByUser_Id(Long userId);

	Optional<NotificationSetting> findByUser_IdAndType(Long userId, NotificationType type);
}
