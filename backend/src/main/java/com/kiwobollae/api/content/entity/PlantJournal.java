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
		@Index(name = "idx_plant_journal_profile_id_written_date", columnList = "plant_profile_id, written_date"),
		@Index(name = "idx_plant_journal_user_id_image_hash_written_date", columnList = "user_id, image_hash, written_date")
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

	@Column(nullable = false, length = 2000)
	private String content;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "image_hash", nullable = false, length = 64)
	private String imageHash;

	@Column(name = "written_date", nullable = false)
	private LocalDate writtenDate;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;
}
