package com.kiwobollae.api.board.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.board.entity.enums.BoardHiddenBy;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "board_comments", indexes = {
		@Index(name = "idx_board_comments_post_id_created_at", columnList = "post_id, created_at"),
		@Index(name = "idx_board_comments_user_id", columnList = "user_id"),
		@Index(name = "idx_board_comments_parent_comment_id", columnList = "parent_comment_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardComment extends BaseTimeEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id", nullable = false)
	private BoardPost post;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// 답글의 답글까지 깊이 제한 없이 허용한다. null이면 최상위 댓글.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_comment_id")
	private BoardComment parentComment;

	@Column(nullable = false, length = 500)
	private String content;

	@Column(name = "like_count", nullable = false)
	private Integer likeCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BoardStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "hidden_by", length = 10)
	private BoardHiddenBy hiddenBy;

	@Column(name = "hidden_at")
	private LocalDateTime hiddenAt;

	public static BoardComment create(BoardPost post, User user, String content, BoardComment parentComment) {
		return BoardComment.builder()
				.post(post)
				.user(user)
				.content(content)
				.parentComment(parentComment)
				.likeCount(0)
				.status(BoardStatus.ACTIVE)
				.build();
	}

	public void updateContent(String content) {
		this.content = content;
	}

	public void hide(BoardHiddenBy hiddenBy, LocalDateTime hiddenAt) {
		this.status = BoardStatus.HIDDEN;
		this.hiddenBy = hiddenBy;
		this.hiddenAt = hiddenAt;
	}

	// likeCount 증감은 엔티티 메서드가 아니라 BoardCommentRepository의 원자적 UPDATE로만 한다 —
	// "로드한 값 + 1"을 dirty checking으로 반영하면 동시 요청에서 갱신 유실(Lost Update)이 생긴다.
}
