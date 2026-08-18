package com.kiwobollae.api.board.dto.response;

import com.kiwobollae.api.board.entity.BoardComment;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import java.time.LocalDateTime;

public record BoardCommentResponse(
		Long id,
		Long postId,
		Long userId,
		String nickname,
		String content,
		Long parentCommentId,
		Integer likeCount,
		BoardStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		Boolean likedByMe
) {
	public static BoardCommentResponse from(BoardComment comment) {
		return from(comment, false);
	}

	// HIDDEN 댓글도 대댓글 트리 연결을 위해 응답에는 포함하되, 실제 내용은 프론트로 보내지 않는다
	// (숨김 사유가 신고/제재일 수 있어 그 내용이 그대로 노출되면 안 된다). 프론트는 status를 보고
	// "삭제된 댓글입니다" 자리표시자를 그려야 한다.
	public static BoardCommentResponse from(BoardComment comment, boolean likedByMe) {
		boolean hidden = comment.getStatus() == BoardStatus.HIDDEN;
		return new BoardCommentResponse(
				comment.getId(),
				comment.getPost().getId(),
				comment.getUser().getId(),
				hidden ? null : comment.getUser().getNickname(),
				hidden ? null : comment.getContent(),
				comment.getParentComment() != null ? comment.getParentComment().getId() : null,
				comment.getLikeCount(),
				comment.getStatus(),
				comment.getCreatedAt(),
				comment.getUpdatedAt(),
				likedByMe
		);
	}
}
