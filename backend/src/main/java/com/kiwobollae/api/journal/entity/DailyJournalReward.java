package com.kiwobollae.api.journal.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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

/** 계정별 KST 일일 성장 일지 보상 판정 기록이다. journalId는 삭제와 무관하게 남기는 논리 참조다. */
@Getter
@Entity
@Table(
		name = "daily_journal_rewards",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_daily_journal_rewards_user_date",
				columnNames = {"user_id", "reward_date"}
		),
		indexes = @Index(name = "idx_daily_journal_rewards_reward_date", columnList = "reward_date")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DailyJournalReward extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "reward_date", nullable = false)
	private LocalDate rewardDate;

	// 기존 식물별 보상 이력을 이관할 때는 원본 일지를 특정할 수 없어 null을 허용한다.
	@Column(name = "journal_id")
	private Long journalId;

	@Column(name = "reward_amount", nullable = false)
	private Long rewardAmount;

	@Column(name = "gacha_draw_id")
	private Long gachaDrawId;

	@Column(name = "rewarded_at", nullable = false)
	private LocalDateTime rewardedAt;

	public void recordGachaDraw(Long gachaDrawId) {
		this.gachaDrawId = gachaDrawId;
	}
}
