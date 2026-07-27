package com.kiwobollae.api.content.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.content.entity.enums.RewardStatus;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "journal_completion_log", indexes = {
		@Index(name = "uq_journal_completion_log_plant_profile_id_completion_date",
				columnList = "plant_profile_id, completion_date", unique = true)
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalCompletionLog extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_profile_id")
	private PlantProfile plantProfile;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_journal_id")
	private PlantJournal plantJournal;

	@Column(name = "completion_date", nullable = false)
	private LocalDate completionDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "reward_status", nullable = false, length = 20)
	private RewardStatus rewardStatus;

	@Column(name = "plant_nickname_snapshot", length = 50)
	private String plantNicknameSnapshot;
}
