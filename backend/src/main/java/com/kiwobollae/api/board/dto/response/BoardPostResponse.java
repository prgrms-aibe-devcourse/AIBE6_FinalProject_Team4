package com.kiwobollae.api.board.dto.response;

import com.kiwobollae.api.board.entity.BoardPost;
import com.kiwobollae.api.board.entity.BoardPostImage;
import com.kiwobollae.api.board.entity.enums.BoardCategory;
import com.kiwobollae.api.board.entity.enums.BoardHiddenBy;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import java.time.LocalDateTime;
import java.util.List;

public record BoardPostResponse(
		Long id,
		Long userId,
		String nickname,
		BoardCategory category,
		String title,
		String content,
		Long journalId,
		List<String> imageUrls,
		Integer viewCount,
		Integer likeCount,
		Integer commentCount,
		BoardStatus status,
		// HIDDEN 상태일 때만 의미가 있다 — 관리자가 숨겼는지(ADMIN) 작성자가 자진 삭제했는지(AUTHOR)
		// 구분해서, 관리자 화면에서 작성자가 지운 글까지 실수로 복원하지 않도록 한다.
		BoardHiddenBy hiddenBy,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		Boolean likedByMe
) {
	public static BoardPostResponse from(BoardPost post) {
		return from(post, List.of(), false);
	}

	public static BoardPostResponse from(BoardPost post, boolean likedByMe) {
		return from(post, List.of(), likedByMe);
	}

	public static BoardPostResponse from(BoardPost post, List<BoardPostImage> images) {
		return from(post, images, false);
	}

	public static BoardPostResponse from(BoardPost post, List<BoardPostImage> images, boolean likedByMe) {
		return new BoardPostResponse(
				post.getId(),
				post.getUser().getId(),
				post.getUser().getNickname(),
				post.getCategory(),
				post.getTitle(),
				post.getContent(),
				post.getJournalId(),
				images.stream().map(BoardPostImage::getImageUrl).toList(),
				post.getViewCount(),
				post.getLikeCount(),
				post.getCommentCount(),
				post.getStatus(),
				post.getHiddenBy(),
				post.getCreatedAt(),
				post.getUpdatedAt(),
				likedByMe
		);
	}
}
