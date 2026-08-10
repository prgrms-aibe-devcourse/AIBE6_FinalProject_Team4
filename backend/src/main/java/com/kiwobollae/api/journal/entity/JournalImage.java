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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "journals_images", indexes = {
		@Index(name = "idx_journals_images_journal_id", columnList = "journal_id"),
		@Index(name = "uq_journals_images_user_id_image_hash_written_date",
				columnList = "user_id, image_hash, written_date", unique = true)
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalImage extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "journal_id", nullable = false)
	private PlantJournal journal;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "image_hash", nullable = false, length = 64)
	private String imageHash;

	@Column(name = "is_representative", nullable = false)
	private boolean representative;

	@Column(name = "written_date", nullable = false)
	private LocalDate writtenDate;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public static JournalImage create(PlantJournal journal, User user, String imageUrl,
			String imageHash, boolean representative, LocalDate writtenDate) {
		return JournalImage.builder()
				.journal(journal)
				.user(user)
				.imageUrl(imageUrl)
				.imageHash(imageHash)
				.representative(representative)
				.writtenDate(writtenDate)
				.createdAt(LocalDateTime.now())
				.build();
	}
}
