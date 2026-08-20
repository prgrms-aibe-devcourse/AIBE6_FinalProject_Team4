package com.kiwobollae.api.board.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.board.dto.request.BoardCommentCreateRequest;
import com.kiwobollae.api.board.dto.request.BoardCommentUpdateRequest;
import com.kiwobollae.api.board.dto.response.BoardCommentResponse;
import com.kiwobollae.api.board.entity.BoardComment;
import com.kiwobollae.api.board.entity.BoardCommentLike;
import com.kiwobollae.api.board.entity.BoardPost;
import com.kiwobollae.api.board.entity.enums.BoardHiddenBy;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import com.kiwobollae.api.board.repository.BoardCommentLikeRepository;
import com.kiwobollae.api.board.repository.BoardCommentRepository;
import com.kiwobollae.api.board.repository.BoardPostRepository;
import com.kiwobollae.api.global.concurrency.UniqueInsertGuard;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardCommentService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String REF_TYPE_POST = "BOARD_POST";
	private static final String REF_TYPE_COMMENT = "BOARD_COMMENT";
	private static final int NOTIFICATION_PREVIEW_LENGTH = 40;

	private final BoardCommentRepository boardCommentRepository;
	private final BoardCommentLikeRepository boardCommentLikeRepository;
	private final BoardPostRepository boardPostRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;
	private final UniqueInsertGuard uniqueInsertGuard;

	@Transactional
	public BoardCommentResponse createComment(Long userId, Long postId, BoardCommentCreateRequest request) {
		BoardPost post = findActivePost(postId);
		BoardComment parent = request.parentCommentId() != null
				? findActiveCommentInPost(request.parentCommentId(), postId)
				: null;
		User user = userRepository.getReferenceById(userId);
		BoardComment comment = boardCommentRepository.save(BoardComment.create(post, user, request.content(), parent));
		boardPostRepository.incrementCommentCount(post.getId());
		notifyOnComment(userId, post, parent, comment);
		return BoardCommentResponse.from(comment);
	}

	// 답글이면 답글이 달린 부모 댓글 작성자에게, 최상위 댓글이면 게시글 작성자에게 알린다.
	// 자기 글/자기 댓글에 스스로 남긴 경우는 알림을 보내지 않는다.
	private void notifyOnComment(Long commenterId, BoardPost post, BoardComment parent, BoardComment comment) {
		String preview = preview(comment.getContent());
		String linkUrl = "/board/" + post.getId();
		if (parent != null) {
			Long parentAuthorId = parent.getUser().getId();
			if (!parentAuthorId.equals(commenterId)) {
				notificationService.notify(
						parentAuthorId, NotificationType.COMMUNITY,
						"내 댓글에 답글이 달렸어요 💬", preview, linkUrl,
						REF_TYPE_COMMENT, comment.getId());
			}
			return;
		}
		Long postAuthorId = post.getUser().getId();
		if (!postAuthorId.equals(commenterId)) {
			notificationService.notify(
					postAuthorId, NotificationType.COMMUNITY,
					"내 게시글에 댓글이 달렸어요 💬", preview, linkUrl,
					REF_TYPE_POST, post.getId());
		}
	}

	private String preview(String content) {
		if (content.length() <= NOTIFICATION_PREVIEW_LENGTH) {
			return "\"" + content + "\"";
		}
		return "\"" + content.substring(0, NOTIFICATION_PREVIEW_LENGTH) + "...\"";
	}

	// 다른 도메인(report)이 댓글 존재 여부만 확인할 때 쓰는 조회 전용 진입점.
	public boolean existsActive(Long id) {
		return boardCommentRepository.findByIdWithUser(id)
				.map(comment -> comment.getStatus() == BoardStatus.ACTIVE)
				.orElse(false);
	}

	public List<BoardCommentResponse> getComments(Long postId, Long userId, boolean isAdmin) {
		// 게시글 상세와 마찬가지로, 관리자가 숨김 처리된 글의 댓글까지 확인할 수 있어야 한다.
		findViewablePost(postId, isAdmin);
		// HIDDEN 댓글도 함께 가져온다 — 부모가 숨겨져도 그 아래 ACTIVE 답글은 트리에서 계속 보여야
		// 하는데, ACTIVE만 가져오면 부모 노드가 없어 답글이 화면에서 통째로 사라져 버린다.
		List<BoardComment> comments = boardCommentRepository.findAllByPostId(postId);
		if (userId == null || comments.isEmpty()) {
			return comments.stream().map(BoardCommentResponse::from).toList();
		}
		List<Long> commentIds = comments.stream().map(BoardComment::getId).toList();
		Set<Long> likedCommentIds = Set.copyOf(boardCommentLikeRepository.findLikedCommentIds(userId, commentIds));
		return comments.stream()
				.map(comment -> BoardCommentResponse.from(comment, likedCommentIds.contains(comment.getId())))
				.toList();
	}

	public Page<BoardCommentResponse> getMyComments(Long userId, Pageable pageable) {
		return boardCommentRepository.findAllByUserId(userId, pageable).map(BoardCommentResponse::from);
	}

	@Transactional
	public BoardCommentResponse updateComment(Long userId, Long commentId, BoardCommentUpdateRequest request) {
		BoardComment comment = findOwnedActiveComment(userId, commentId);
		comment.updateContent(request.content());
		return BoardCommentResponse.from(comment);
	}

	@Transactional
	public void deleteComment(Long userId, Long commentId) {
		BoardComment comment = findOwnedActiveComment(userId, commentId);
		comment.hide(BoardHiddenBy.AUTHOR, LocalDateTime.now(KST));
		boardPostRepository.decrementCommentCount(comment.getPost().getId());
	}

	// 신고 검토 화면에서 관리자가 신고된 댓글의 원문/게시글을 확인할 때 쓴다. findActiveComment와
	// 달리 상태를 ACTIVE로 제한하지 않는다 — 신고를 완료 처리하면 먼저 댓글을 HIDDEN으로 바꾸고,
	// 작성자가 직접 삭제해도 HIDDEN이 되므로, ACTIVE만 허용하면 "완료된 신고"의 상세 보기가 항상
	// 실패한다. BoardCommentResponse.from이 HIDDEN이면 nickname/content를 이미 null로 가려주므로
	// 그대로 반환해도 안전하다.
	public BoardCommentResponse getForAdmin(Long commentId) {
		BoardComment comment = boardCommentRepository.findByIdWithUser(commentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND));
		return BoardCommentResponse.from(comment);
	}

	@Transactional
	public void adminHideComment(Long commentId) {
		BoardComment comment = findActiveComment(commentId);
		comment.hide(BoardHiddenBy.ADMIN, LocalDateTime.now(KST));
		boardPostRepository.decrementCommentCount(comment.getPost().getId());
	}

	@Transactional
	public void likeComment(Long userId, Long commentId) {
		BoardComment comment = findActiveComment(commentId);
		if (boardCommentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
			throw new BusinessException(ErrorCode.BOARD_ALREADY_LIKED);
		}
		User user = userRepository.getReferenceById(userId);
		// existsBy 사전 체크와 저장 사이의 동시성 경쟁으로 유니크 제약이 위반돼도 원시 DB 에러 대신
		// "이미 좋아요를 눌렀다"는 안내로 보이게 하고, UniqueInsertGuard로 별도 트랜잭션에 격리해
		// 이 요청 자체의 트랜잭션이 오염되지 않게 한다.
		boolean saved = uniqueInsertGuard.tryInsert(() ->
				boardCommentLikeRepository.saveAndFlush(BoardCommentLike.create(comment, user, LocalDateTime.now(KST))));
		if (!saved) {
			throw new BusinessException(ErrorCode.BOARD_ALREADY_LIKED);
		}
		boardCommentRepository.incrementLikeCount(commentId);
	}

	@Transactional
	public void unlikeComment(Long userId, Long commentId) {
		BoardCommentLike like = boardCommentLikeRepository.findByCommentIdAndUserId(commentId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOARD_LIKE_NOT_FOUND));
		boardCommentLikeRepository.delete(like);
		boardCommentRepository.decrementLikeCount(commentId);
	}

	private BoardComment findOwnedActiveComment(Long userId, Long commentId) {
		BoardComment comment = findActiveComment(commentId);
		if (!comment.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.BOARD_COMMENT_NOT_OWNED);
		}
		return comment;
	}

	private BoardComment findActiveComment(Long commentId) {
		BoardComment comment = boardCommentRepository.findByIdWithUser(commentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND));
		if (comment.getStatus() != BoardStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND);
		}
		return comment;
	}

	// 답글의 부모 댓글은 같은 게시글에 속한 활성 댓글이어야 한다. 다른 게시글의 댓글을 부모로
	// 지정하거나, 숨겨진/존재하지 않는 댓글을 지정하면 전부 "댓글을 찾을 수 없음"으로 취급한다.
	private BoardComment findActiveCommentInPost(Long commentId, Long postId) {
		BoardComment parent = findActiveComment(commentId);
		if (!parent.getPost().getId().equals(postId)) {
			throw new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND);
		}
		return parent;
	}

	private BoardPost findActivePost(Long postId) {
		return findViewablePost(postId, false);
	}

	private BoardPost findViewablePost(Long postId, boolean allowHidden) {
		BoardPost post = boardPostRepository.findById(postId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
		if (post.getStatus() != BoardStatus.ACTIVE && !allowHidden) {
			throw new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND);
		}
		return post;
	}
}
