package com.kiwobollae.api.notification.repository;

import com.kiwobollae.api.notification.entity.JournalReminderLog;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalReminderLogRepository extends JpaRepository<JournalReminderLog, Long> {

	boolean existsByUser_IdAndReminderDate(Long userId, LocalDate reminderDate);
}
