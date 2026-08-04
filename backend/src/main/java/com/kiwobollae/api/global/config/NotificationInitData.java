package com.kiwobollae.api.global.config;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.notification.entity.Notification;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only sample alerts so the notification bell/목록/설정 화면 isn't empty for the
 * InitData test users. One notification per {@link NotificationType} per user, spread
 * across today/yesterday with a mix of read/unread so date grouping and the unread
 * badge both have something to show.
 *
 * <p>Depends on InitData (users, @Order(1)) having already run; skips silently if a
 * seed user is missing. Disable without changing code by setting
 * {@code app.seed.notification.enabled=false}.
 */
@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(prefix = "app.seed.notification", name = "enabled", havingValue = "true")
@Order(5)
@RequiredArgsConstructor
public class NotificationInitData implements ApplicationRunner {

	private record Sample(NotificationType type, String title, String content, String linkUrl, boolean read, int daysAgo) {
	}

	private static final List<Sample> SAMPLES = List.of(
			new Sample(NotificationType.DELIVERY, "주문하신 상품이 배송을 시작했어요 📦", "ORD-20260709-0022 · 방울토마토 모종", "/my/orders", false, 0),
			new Sample(NotificationType.POINT, "일지 보상 30P가 지급됐어요 ☀️", "토실이의 오늘 기록", "/my/points", false, 0),
			new Sample(NotificationType.JOURNAL_REMINDER, "오늘 쌈싸리의 모습을 남겨볼까요? 🌱", "아직 오늘의 일지를 쓰지 않으셨어요", "/journals", true, 0),
			new Sample(NotificationType.COMMUNITY, "내 게시글에 댓글이 달렸어요 💬", "\"저도 이 방법으로 키우고 있어요!\"", null, false, 1),
			new Sample(NotificationType.INQUIRY, "문의하신 내용에 답변이 도착했어요 💬", "배송 관련 문의", "/my/inquiries", true, 1),
			new Sample(NotificationType.NOTICE, "새로운 카드가 상점에 입고됐어요 📢", "감자 카드를 만나보세요", "/cards", true, 1)
	);

	private static final List<String> SEED_EMAILS = List.of("admin@test.com", "test@test.com", "user@test.com");

	private final UserRepository userRepository;
	private final NotificationRepository notificationRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (notificationRepository.count() > 0) {
			return;
		}

		for (String email : SEED_EMAILS) {
			User user = userRepository.findByEmail(email).orElse(null);
			if (user == null) {
				continue;
			}
			for (Sample sample : SAMPLES) {
				Notification notification = Notification.create(
						user,
						sample.type(),
						sample.title(),
						sample.content(),
						sample.linkUrl(),
						null,
						null,
						LocalDateTime.now().minusDays(sample.daysAgo())
				);
				if (sample.read()) {
					notification.markRead(notification.getCreatedAt());
				}
				notificationRepository.save(notification);
			}
		}
	}
}
