package com.kiwobollae.api.notification.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정별 KST 일일 일지 작성 리마인더 발송 여부를 잠그는 전용 테이블이다. notification 테이블의
 * (user_id, type, ref_type, ref_id) 인덱스는 다른 알림 종류들이 같은 refType/refId를 배송
 * 시작→완료처럼 여러 번 재사용하기 때문에 테이블 전체에 유니크 제약을 걸 수 없다 — 이 리마인더만
 * "계정·날짜당 최대 1건"이 보장되면 되므로 별도 테이블에 유니크 제약을 둬서 로그인/토큰 재발급이
 * 동시에 들어와도 DB가 원자적으로 승자를 하나만 남긴다.
 */
@Getter
@Entity
@Table(
		name = "journal_reminder_logs",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_journal_reminder_logs_user_date",
				columnNames = {"user_id", "reminder_date"}
		)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalReminderLog extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "reminder_date", nullable = false)
	private LocalDate reminderDate;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static JournalReminderLog create(User user, LocalDate reminderDate, LocalDateTime createdAt) {
		return JournalReminderLog.builder()
				.user(user)
				.reminderDate(reminderDate)
				.createdAt(createdAt)
				.build();
	}
}
