package com.kiwobollae.api.board.entity;

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
@Table(name = "board_post_images", indexes = {
		@Index(name = "idx_board_post_images_post_id", columnList = "post_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardPostImage extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id", nullable = false)
	private BoardPost post;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	// 첨부 순서를 그대로 보존하기 위한 0-base 인덱스.
	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static BoardPostImage create(BoardPost post, String imageUrl, int sortOrder, LocalDateTime createdAt) {
		return BoardPostImage.builder()
				.post(post)
				.imageUrl(imageUrl)
				.sortOrder(sortOrder)
				.createdAt(createdAt)
				.build();
	}
}
