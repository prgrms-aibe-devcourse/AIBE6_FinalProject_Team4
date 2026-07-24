package com.kiwobollae.api.notification.repository;

import com.kiwobollae.api.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
