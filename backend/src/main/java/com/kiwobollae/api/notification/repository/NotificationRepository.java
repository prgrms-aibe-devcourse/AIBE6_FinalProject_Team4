package com.kiwobollae.api.notification.repository;

import com.kiwobollae.api.notification.entity.Notification;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@Query(value = "select n from Notification n where n.user.id = :userId and n.createdAt >= :retentionCutoff "
			+ "and (:type is null or n.type = :type)",
			countQuery = "select count(n) from Notification n where n.user.id = :userId "
					+ "and n.createdAt >= :retentionCutoff and (:type is null or n.type = :type)")
	Page<Notification> search(
			@Param("userId") Long userId,
			@Param("type") NotificationType type,
			@Param("retentionCutoff") LocalDateTime retentionCutoff,
			Pageable pageable
	);

	@Query("select n from Notification n where n.id = :id and n.user.id = :userId")
	Optional<Notification> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	// 목록 조회(search)와 동일하게 보관 기간이 지난 알림은 배지 카운트에서도 제외한다 —
	// 그렇지 않으면 화면에 안 보이는 알림 때문에 배지 수가 실제 목록과 어긋난다.
	long countByUser_IdAndIsReadFalseAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime retentionCutoff);

	@Modifying
	@Query("update Notification n set n.isRead = true, n.readAt = :readAt "
			+ "where n.user.id = :userId and n.isRead = false")
	int markAllReadByUserId(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
