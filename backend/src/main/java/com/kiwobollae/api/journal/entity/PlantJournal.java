package com.kiwobollae.api.journal.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "plant_journals", indexes = {
		@Index(name = "idx_plant_journal_user_id_written_date", columnList = "user_id, written_date"),
		@Index(name = "idx_plant_journal_profile_id_written_date", columnList = "plant_profile_id, written_date")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlantJournal extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_profile_id", nullable = false)
	private PlantProfile plantProfile;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(length = 2000)
	private String content;

	@Column(name = "written_date", nullable = false)
	private LocalDate writtenDate;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public static PlantJournal create(User user, PlantProfile plantProfile, String content, LocalDate writtenDate) {
		return create(user, plantProfile, content, writtenDate, LocalDateTime.now());
	}

	/** 데이터 이관·초기 데이터처럼 실제 작성 시각이 이미 정해진 일지를 생성한다. */
	public static PlantJournal create(
			User user,
			PlantProfile plantProfile,
			String content,
			LocalDate writtenDate,
			LocalDateTime createdAt
	) {
		return PlantJournal.builder()
				.user(user)
				.plantProfile(plantProfile)
				.content(content)
				.writtenDate(writtenDate)
				.createdAt(createdAt)
				.updatedAt(createdAt)
				.build();
	}

	public void updateContent(String content) {
		if (content != null) {
			this.content = content;
		}
		this.updatedAt = LocalDateTime.now();
	}

	public void softDelete(LocalDateTime now) {
		this.deletedAt = now;
	}
}
