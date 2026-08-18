package com.kiwobollae.api.board.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "board_comment_likes",
		uniqueConstraints = @UniqueConstraint(name = "uk_board_comment_likes_comment_id_user_id", columnNames = {"comment_id", "user_id"}),
		indexes = @Index(name = "idx_board_comment_likes_user_id", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardCommentLike extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "comment_id", nullable = false)
	private BoardComment comment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static BoardCommentLike create(BoardComment comment, User user, LocalDateTime createdAt) {
		return BoardCommentLike.builder()
				.comment(comment)
				.user(user)
				.createdAt(createdAt)
				.build();
	}
}
