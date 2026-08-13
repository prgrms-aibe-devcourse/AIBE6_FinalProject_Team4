package com.kiwobollae.api.board.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.board.dto.request.BoardPostCreateRequest;
import com.kiwobollae.api.board.dto.request.BoardPostUpdateRequest;
import com.kiwobollae.api.board.dto.response.BoardPostResponse;
import com.kiwobollae.api.board.entity.BoardPost;
import com.kiwobollae.api.board.entity.BoardPostImage;
import com.kiwobollae.api.board.entity.BoardPostLike;
import com.kiwobollae.api.board.entity.BoardPostView;
import com.kiwobollae.api.board.entity.enums.BoardCategory;
import com.kiwobollae.api.board.entity.enums.BoardHiddenBy;
import com.kiwobollae.api.board.entity.enums.BoardSearchType;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import com.kiwobollae.api.board.repository.BoardPostImageRepository;
import com.kiwobollae.api.board.repository.BoardPostLikeRepository;
import com.kiwobollae.api.board.repository.BoardPostRepository;
import com.kiwobollae.api.board.repository.BoardPostViewRepository;
import com.kiwobollae.api.journal.dto.response.PlantJournalResponse;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.journal.service.PlantJournalService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardPostService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final BoardPostRepository boardPostRepository;
	private final BoardPostLikeRepository boardPostLikeRepository;
	private final BoardPostViewRepository boardPostViewRepository;
	private final BoardPostImageRepository boardPostImageRepository;
	private final UserRepository userRepository;
	private final PlantJournalRepository plantJournalRepository;
	private final PlantJournalService plantJournalService;
	private final BoardImageUploadService boardImageUploadService;

	@Transactional
	public BoardPostResponse createPost(Long userId, BoardPostCreateRequest request) {
		// 인증된 요청이라 행 존재가 보장되므로 조회 없이 참조만 얻는다. role은 NOTICE 검증 시에만 지연 로딩된다.
		User user = userRepository.getReferenceById(userId);

		if (request.category() == BoardCategory.NOTICE && user.getRole() != UserRole.ADMIN) {
			throw new BusinessException(ErrorCode.BOARD_NOTICE_FORBIDDEN);
		}

		Long journalId = null;
		if (request.category() == BoardCategory.PLANT_QNA) {
			if (request.journalId() == null) {
				throw new BusinessException(ErrorCode.BOARD_JOURNAL_REQUIRED);
			}
			plantJournalRepository.findOwnedActive(request.journalId(), userId)
					.orElseThrow(() -> new BusinessException(ErrorCode.BOARD_JOURNAL_NOT_OWNED));
			journalId = request.journalId();
		}

		BoardPost post = boardPostRepository.save(
				BoardPost.create(user, request.category(), request.title(), request.content(), journalId)
		);
		List<BoardPostImage> images = saveImages(post, request.imageUrls());
		return BoardPostResponse.from(post, images);
	}

	public Page<BoardPostResponse> getPosts(
			BoardCategory category, String keyword, BoardSearchType searchType, Pageable pageable, Long userId) {
		String trimmedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
		BoardSearchType effectiveSearchType = searchType == null ? BoardSearchType.TITLE_CONTENT : searchType;
		Page<BoardPost> posts = boardPostRepository.search(
				BoardStatus.ACTIVE, category, trimmedKeyword, effectiveSearchType.name(), pageable);
		if (posts.isEmpty()) {
			return posts.map(post -> BoardPostResponse.from(post, List.of()));
		}
		List<Long> postIds = posts.map(BoardPost::getId).toList();
		Map<Long, List<BoardPostImage>> imagesByPost = loadImagesByPost(postIds);
		Set<Long> likedPostIds = userId == null
				? Set.of()
				: Set.copyOf(boardPostLikeRepository.findLikedPostIds(userId, postIds));
		return posts.map(post -> BoardPostResponse.from(
				post, imagesByPost.getOrDefault(post.getId(), List.of()), likedPostIds.contains(post.getId())));
	}

	@Transactional
	public BoardPostResponse getPost(Long id, Long userId, String viewerIp) {
		BoardPost post = findActivePost(id);
		if (viewerIp != null && !viewerIp.isBlank()) {
			recordViewOnce(post, viewerIp);
		}
		boolean likedByMe = userId != null && boardPostLikeRepository.existsByPostIdAndUserId(id, userId);
		List<BoardPostImage> images = boardPostImageRepository.findByPostIdOrderBySortOrderAsc(id);
		return BoardPostResponse.from(post, images, likedByMe);
	}

	// 같은 IP의 재조회는 조회수를 올리지 않는다. existsBy 사전 체크와 저장 사이의 동시성 경쟁으로
	// 유니크 제약이 위반돼도(동시에 첫 조회가 들어온 경우) 조용히 무시하고 넘어간다 — 좋아요와
	// 달리 실패를 사용자에게 보여줄 필요가 없는 부가 지표다.
	private void recordViewOnce(BoardPost post, String viewerIp) {
		if (boardPostViewRepository.existsByPostIdAndIpAddress(post.getId(), viewerIp)) {
			return;
		}
		try {
			boardPostViewRepository.saveAndFlush(BoardPostView.create(post, viewerIp, LocalDateTime.now(KST)));
		} catch (DataIntegrityViolationException e) {
			return;
		}
		post.incrementViewCount();
	}

	public Page<BoardPostResponse> getMyPosts(Long userId, Pageable pageable) {
		return boardPostRepository.findAllByUserId(userId, pageable).map(BoardPostResponse::from);
	}

	// PLANT_QNA 게시글에 연동된 일지는 작성자가 아닌 다른 열람자도 볼 수 있어야 한다(글을 올린
	// 시점에 본인이 공유하기로 선택한 것이므로). 게시글이 실제로 그 일지를 연동하고 있는지 여기서
	// 확인한 뒤에만 PlantJournalService의 소유자 무관 조회로 넘긴다.
	public PlantJournalResponse getLinkedJournal(Long postId) {
		BoardPost post = findActivePost(postId);
		if (post.getJournalId() == null) {
			throw new BusinessException(ErrorCode.BOARD_JOURNAL_NOT_LINKED);
		}
		return plantJournalService.getPublicSnapshot(post.getJournalId());
	}

	// 다른 도메인(report)이 게시글 존재 여부만 확인할 때 쓰는 조회 전용 진입점.
	public boolean existsActive(Long id) {
		return boardPostRepository.findById(id)
				.map(post -> post.getStatus() == BoardStatus.ACTIVE)
				.orElse(false);
	}

	@Transactional
	public BoardPostResponse updatePost(Long userId, Long id, BoardPostUpdateRequest request) {
		BoardPost post = findOwnedActivePost(userId, id);
		post.update(request.title(), request.content());

		// 이미지 전체 교체: 기존 이미지를 먼저 지우고 새 목록을 저장한다. 그대로 남은(교체 안 한)
		// 이미지의 S3 객체까지 지우면 안 되므로, 새 목록에서 실제로 빠진 것만 골라 정리한다.
		List<BoardPostImage> oldImages = boardPostImageRepository.findByPostIdOrderBySortOrderAsc(id);
		boardPostImageRepository.deleteByPostId(id);
		List<BoardPostImage> images = saveImages(post, request.imageUrls());

		Set<String> keptUrls = Set.copyOf(request.imageUrls());
		oldImages.stream()
				.map(BoardPostImage::getImageUrl)
				.filter(url -> !keptUrls.contains(url))
				.forEach(url -> boardImageUploadService.delete(url, userId));

		return BoardPostResponse.from(post, images);
	}

	@Transactional
	public void deletePost(Long userId, Long id) {
		BoardPost post = findOwnedActivePost(userId, id);
		hidePostAndCleanImages(post, BoardHiddenBy.AUTHOR, userId);
	}

	@Transactional
	public void adminHidePost(Long id) {
		BoardPost post = findActivePost(id);
		hidePostAndCleanImages(post, BoardHiddenBy.ADMIN, post.getUser().getId());
	}

	// 숨김 처리는 복구 API가 없어 사실상 영구 삭제와 같으므로, 더 이상 어떤 게시글도 참조하지
	// 않는 S3 객체를 이 시점에 정리한다(성장 일지의 deleteJournal과 동일한 컨벤션).
	private void hidePostAndCleanImages(BoardPost post, BoardHiddenBy hiddenBy, Long ownerUserId) {
		List<BoardPostImage> images = boardPostImageRepository.findByPostIdOrderBySortOrderAsc(post.getId());
		post.hide(hiddenBy, LocalDateTime.now(KST));
		images.forEach(image -> boardImageUploadService.delete(image.getImageUrl(), ownerUserId));
	}

	private List<BoardPostImage> saveImages(BoardPost post, List<String> imageUrls) {
		if (imageUrls.size() > 1) {
			throw new BusinessException(ErrorCode.BOARD_IMAGE_LIMIT_EXCEEDED);
		}
		List<BoardPostImage> images = new ArrayList<>();
		for (int i = 0; i < imageUrls.size(); i++) {
			images.add(BoardPostImage.create(post, imageUrls.get(i), i, LocalDateTime.now(KST)));
		}
		return boardPostImageRepository.saveAll(images);
	}

	// 페이지에 담긴 게시글들의 이미지를 한 번에 로딩해 postId로 묶는다 (개별 조회로 인한 N+1 방지).
	private Map<Long, List<BoardPostImage>> loadImagesByPost(List<Long> postIds) {
		return boardPostImageRepository.findByPostIdIn(postIds).stream()
				.collect(Collectors.groupingBy(image -> image.getPost().getId()));
	}

	@Transactional
	public void likePost(Long userId, Long id) {
		BoardPost post = findActivePost(id);
		if (boardPostLikeRepository.existsByPostIdAndUserId(id, userId)) {
			throw new BusinessException(ErrorCode.BOARD_ALREADY_LIKED);
		}
		User user = userRepository.getReferenceById(userId);
		try {
			// existsBy 사전 체크와 저장 사이에는 동시성 경쟁이 있을 수 있다(더블 클릭, 중복 요청 등).
			// 유니크 제약 위반이 원시 DB 에러로 새는 대신 "이미 좋아요를 눌렀다"는 안내로 보이게 한다.
			boardPostLikeRepository.saveAndFlush(BoardPostLike.create(post, user, LocalDateTime.now(KST)));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.BOARD_ALREADY_LIKED);
		}
		post.incrementLikeCount();
	}

	@Transactional
	public void unlikePost(Long userId, Long id) {
		BoardPostLike like = boardPostLikeRepository.findByPostIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOARD_LIKE_NOT_FOUND));
		boardPostLikeRepository.delete(like);
		boardPostRepository.getReferenceById(id).decrementLikeCount();
	}

	private BoardPost findOwnedActivePost(Long userId, Long id) {
		BoardPost post = findActivePost(id);
		if (!post.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.BOARD_POST_NOT_OWNED);
		}
		return post;
	}

	private BoardPost findActivePost(Long id) {
		BoardPost post = boardPostRepository.findByIdWithUser(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
		if (post.getStatus() != BoardStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND);
		}
		return post;
	}
}
