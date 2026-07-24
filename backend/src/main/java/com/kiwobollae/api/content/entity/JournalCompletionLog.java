package com.kiwobollae.api.content.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
		@Index(name = "idx_journal_completion_log_user_id_completion_date", columnList = "user_id, completion_date", unique = true)
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalCompletionLog extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_profile_id", nullable = false)
	private PlantProfile plantProfile;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_journal_id", nullable = false)
	private PlantJournal plantJournal;

	@Column(name = "completion_date", nullable = false)
	private LocalDate completionDate;
}
