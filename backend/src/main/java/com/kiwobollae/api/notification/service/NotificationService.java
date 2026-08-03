package com.kiwobollae.api.notification.service;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.notification.dto.response.NotificationResponse;
import com.kiwobollae.api.notification.dto.response.NotificationSettingResponse;
import com.kiwobollae.api.notification.dto.response.UnreadCountResponse;
import com.kiwobollae.api.notification.entity.Notification;
import com.kiwobollae.api.notification.entity.NotificationSetting;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.repository.NotificationRepository;
import com.kiwobollae.api.notification.repository.NotificationSettingRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	// ALERT-01: 보관 기간이 지난 알림은 목록에서 제외한다. 물리 삭제는 하지 않고 조회 시에만
	// 걸러낸다 — 배치로 오래된 행을 지우는 정책은 TODO(팀 확인 필요).
	private static final long RETENTION_DAYS = 30L;

	private final NotificationRepository notificationRepository;
	private final NotificationSettingRepository notificationSettingRepository;
	private final UserRepository userRepository;

	public Page<NotificationResponse> getNotifications(Long userId, NotificationType type, Pageable pageable) {
		return notificationRepository.search(userId, type, retentionCutoff(), pageable)
				.map(NotificationResponse::from);
	}

	// getNotifications와 같은 보관 기간 기준을 써야 한다 — 그렇지 않으면 목록에는 안 보이는
	// 알림이 배지 수에는 남아 있는 불일치가 생긴다.
	public UnreadCountResponse getUnreadCount(Long userId) {
		return new UnreadCountResponse(
				notificationRepository.countByUser_IdAndIsReadFalseAndCreatedAtGreaterThanEqual(userId, retentionCutoff())
		);
	}

	private LocalDateTime retentionCutoff() {
		return LocalDateTime.now(KST).minusDays(RETENTION_DAYS);
	}

	@Transactional
	public NotificationResponse markRead(Long userId, Long notificationId) {
		Notification notification = findOwnedNotification(userId, notificationId);
		notification.markRead(LocalDateTime.now(KST));
		return NotificationResponse.from(notification);
	}

	@Transactional
	public void markAllRead(Long userId) {
		notificationRepository.markAllReadByUserId(userId, LocalDateTime.now(KST));
	}

	@Transactional
	public void deleteNotification(Long userId, Long notificationId) {
		Notification notification = findOwnedNotification(userId, notificationId);
		notificationRepository.delete(notification);
	}

	/**
	 * 유형별 수신 설정을 전부 반환한다. 사용자가 한 번도 바꾸지 않은 유형은 저장된 행이 없으므로,
	 * 기본값(수신 허용)을 그 자리에서 만들어 채운다 — 회원가입 시 6종을 미리 깔아두지 않는다.
	 */
	public List<NotificationSettingResponse> getSettings(Long userId) {
		Map<NotificationType, NotificationSetting> saved = notificationSettingRepository.findAllByUser_Id(userId)
				.stream()
				.collect(Collectors.toMap(NotificationSetting::getType, setting -> setting));

		return Arrays.stream(NotificationType.values())
				.map(type -> saved.containsKey(type)
						? NotificationSettingResponse.from(saved.get(type))
						: new NotificationSettingResponse(null, userId, type, true, null))
				.toList();
	}

	@Transactional
	public NotificationSettingResponse updateSetting(Long userId, NotificationType type, boolean enabled) {
		LocalDateTime now = LocalDateTime.now(KST);
		NotificationSetting setting = notificationSettingRepository.findByUser_IdAndType(userId, type)
				.orElse(null);
		if (setting == null) {
			setting = notificationSettingRepository.save(
					NotificationSetting.create(userRepository.getReferenceById(userId), type, enabled, now)
			);
		} else {
			setting.changeEnabled(enabled, now);
		}
		return NotificationSettingResponse.from(setting);
	}

	/**
	 * 다른 도메인이 이벤트 발생 시 호출하는 알림 생성 진입점(ALERT-08). API가 아니라 내부 호출
	 * 전용이며, 수신 설정을 확인해 꺼져 있으면 알림을 만들지 않고 조용히 건너뛴다.
	 *
	 * <p>실시간 전달(SSE/폴링) 여부는 아직 결정되지 않아 이 메서드는 저장까지만 담당한다 —
	 * TODO(팀 확인 필요).
	 */
	@Transactional
	public void notify(
			Long userId,
			NotificationType type,
			String title,
			String content,
			String linkUrl,
			String refType,
			Long refId
	) {
		boolean enabled = notificationSettingRepository.findByUser_IdAndType(userId, type)
				.map(NotificationSetting::getEnabled)
				.orElse(true);
		if (!enabled) {
			return;
		}
		notificationRepository.save(Notification.create(
				userRepository.getReferenceById(userId),
				type,
				title,
				content,
				linkUrl,
				refType,
				refId,
				LocalDateTime.now(KST)
		));
	}

	private Notification findOwnedNotification(Long userId, Long notificationId) {
		return notificationRepository.findByIdAndUserId(notificationId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
	}
}
