package com.kiwobollae.api.board.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.board.entity.enums.BoardCategory;
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
@Table(name = "board_posts", indexes = {
		@Index(name = "idx_board_posts_category_created_at", columnList = "category, created_at"),
		@Index(name = "idx_board_posts_user_id", columnList = "user_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardPost extends BaseTimeEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BoardCategory category;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 2000)
	private String content;

	// PLANT_QNA에서만 사용. 논리 참조(FK 없음) — 일지가 나중에 삭제/비공개돼도 게시글은 남는다.
	@Column(name = "journal_id")
	private Long journalId;

	@Column(name = "view_count", nullable = false)
	private Integer viewCount;

	@Column(name = "like_count", nullable = false)
	private Integer likeCount;

	@Column(name = "comment_count", nullable = false)
	private Integer commentCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BoardStatus status;

	// orders.cancelledBy와 동일한 컨벤션 — 작성자 본인이 지웠는지 관리자가 숨겼는지 구분한다.
	// HIDDEN일 때만 값이 있다.
	@Enumerated(EnumType.STRING)
	@Column(name = "hidden_by", length = 10)
	private BoardHiddenBy hiddenBy;

	@Column(name = "hidden_at")
	private LocalDateTime hiddenAt;

	public static BoardPost create(User user, BoardCategory category, String title, String content, Long journalId) {
		return BoardPost.builder()
				.user(user)
				.category(category)
				.title(title)
				.content(content)
				.journalId(journalId)
				.viewCount(0)
				.likeCount(0)
				.commentCount(0)
				.status(BoardStatus.ACTIVE)
				.build();
	}

	// products.category처럼 카테고리는 생성 후 변경하지 않는 컨벤션을 따른다. 제목/본문만 수정한다.
	public void update(String title, String content) {
		this.title = title;
		this.content = content;
	}

	public void hide(BoardHiddenBy hiddenBy, LocalDateTime hiddenAt) {
		this.status = BoardStatus.HIDDEN;
		this.hiddenBy = hiddenBy;
		this.hiddenAt = hiddenAt;
	}

	// board_post_likes/board_comments를 매번 COUNT하지 않도록 비정규화 캐시로 둔다. 증감은
	// 엔티티 메서드가 아니라 BoardPostRepository의 원자적 UPDATE로만 한다 — "로드한 값 + 1"을
	// dirty checking으로 반영하면 동시 요청에서 갱신 유실(Lost Update)이 생긴다.
}
