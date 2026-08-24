package com.kiwobollae.api.notification.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.concurrency.UniqueInsertGuard;
import com.kiwobollae.api.journal.repository.DailyJournalRewardRepository;
import com.kiwobollae.api.notification.entity.JournalReminderLog;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.repository.JournalReminderLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘치 일지 보상을 아직 받지 않은 사용자에게 로그인 시 한 번만 작성을 유도한다.
 *
 * <p>로그인/토큰 재발급 응답과는 무관한 best-effort 부가 기능이라 별도 스레드({@code @Async})로
 * 뺐다 — journal_reminder_logs에 FK로 걸린 users row가 다른 트랜잭션에 잠겨있으면 INSERT가
 * MySQL lock_wait_timeout(기본 50초)만큼 블로킹될 수 있는데, 로그인 요청 스레드에서 그대로
 * 실행하면 사용자가 그 대기를 고스란히 체감하게 된다.
 *
 * <p>existsBy 확인과 저장 사이에 동시 요청이 끼어들면 둘 다 "아직 안 보냄"으로 보고 중복
 * 저장할 수 있어, 전용 잠금 테이블(journal_reminder_logs)에 유니크 제약을 걸고
 * UniqueInsertGuard로 원자적으로 하나만 승리하게 한다 — 이긴 요청만 실제 알림을 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalReminderNotifier {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String JOURNAL_REMINDER_REF_TYPE = "JOURNAL_REMINDER_DATE";
	private static final String JOURNAL_REMINDER_TITLE = "아직 오늘 일지를 작성하지 않았어요";
	private static final String JOURNAL_REMINDER_CONTENT = "오늘의 성장 일지를 남기고 보상을 받아보세요.";
	private static final String JOURNAL_REMINDER_LINK_URL = "/journals/new";

	private final UserRepository userRepository;
	private final NotificationService notificationService;
	private final JournalReminderLogRepository journalReminderLogRepository;
	private final UniqueInsertGuard uniqueInsertGuard;
	private final DailyJournalRewardRepository dailyJournalRewardRepository;

	@Async("journalReminderTaskExecutor")
	public void sendIfNeeded(Long userId) {
		try {
			LocalDate today = LocalDate.now(KST);
			if (dailyJournalRewardRepository.existsForUserAndRewardDate(userId, today)) {
				return;
			}
			if (journalReminderLogRepository.existsByUser_IdAndReminderDate(userId, today)) {
				return;
			}
			User userRef = userRepository.getReferenceById(userId);
			boolean claimed = uniqueInsertGuard.tryInsert(() ->
					journalReminderLogRepository.saveAndFlush(
							JournalReminderLog.create(userRef, today, LocalDateTime.now(KST))));
			if (!claimed) {
				return;
			}
			notificationService.notify(
					userId,
					NotificationType.JOURNAL_REMINDER,
					JOURNAL_REMINDER_TITLE,
					JOURNAL_REMINDER_CONTENT,
					JOURNAL_REMINDER_LINK_URL,
					JOURNAL_REMINDER_REF_TYPE,
					today.toEpochDay()
			);
		} catch (Exception e) {
			// 로그인 요청과 완전히 분리된 스레드에서 실행되므로, 여기서 실패해도 호출부에
			// 전달할 방법이 없다 — 로그로만 남기고 다음 로그인 때 다시 시도되게 둔다.
			log.warn("Failed to send journal reminder for user {}", userId, e);
		}
	}
}
