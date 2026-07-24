package com.kiwobollae.api.content.entity;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "journals_images", indexes = {
		@Index(name = "idx_journals_images_journal_id", columnList = "journal_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalImage extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "journal_id", nullable = false)
	private PlantJournal journal;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "sort_order", nullable = false)
	private boolean sortOrder;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}
