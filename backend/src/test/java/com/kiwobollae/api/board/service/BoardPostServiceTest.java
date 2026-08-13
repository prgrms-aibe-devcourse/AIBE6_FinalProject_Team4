package com.kiwobollae.api.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.board.dto.request.BoardPostCreateRequest;
import com.kiwobollae.api.board.dto.request.BoardPostUpdateRequest;
import com.kiwobollae.api.board.dto.response.BoardPostResponse;
import com.kiwobollae.api.board.entity.BoardPost;
import com.kiwobollae.api.board.entity.BoardPostLike;
import com.kiwobollae.api.board.entity.enums.BoardCategory;
import com.kiwobollae.api.board.entity.enums.BoardHiddenBy;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import com.kiwobollae.api.board.repository.BoardPostImageRepository;
import com.kiwobollae.api.board.repository.BoardPostLikeRepository;
import com.kiwobollae.api.board.repository.BoardPostRepository;
import com.kiwobollae.api.board.repository.BoardPostViewRepository;
import com.kiwobollae.api.content.dto.response.PlantJournalResponse;
import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.content.service.PlantJournalService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BoardPostServiceTest {

	@Mock private BoardPostRepository boardPostRepository;
	@Mock private BoardPostLikeRepository boardPostLikeRepository;
	@Mock private BoardPostViewRepository boardPostViewRepository;
	@Mock private BoardPostImageRepository boardPostImageRepository;
	@Mock private UserRepository userRepository;
	@Mock private PlantJournalRepository plantJournalRepository;
	@Mock private PlantJournalService plantJournalService;
	@Mock private BoardImageUploadService boardImageUploadService;
	@InjectMocks private BoardPostService boardPostService;

	private User mockUser(Long id, UserRole role) {
		User user = mock(User.class);
		lenient().when(user.getId()).thenReturn(id);
		lenient().when(user.getRole()).thenReturn(role);
		lenient().when(user.getNickname()).thenReturn("초록이");
		return user;
	}

	private BoardPost mockPost(Long id, User user, BoardStatus status) {
		BoardPost post = mock(BoardPost.class);
		lenient().when(post.getId()).thenReturn(id);
		lenient().when(post.getUser()).thenReturn(user);
		lenient().when(post.getCategory()).thenReturn(BoardCategory.FREE);
		lenient().when(post.getTitle()).thenReturn("제목");
		lenient().when(post.getContent()).thenReturn("내용");
		lenient().when(post.getViewCount()).thenReturn(0);
		lenient().when(post.getLikeCount()).thenReturn(0);
		lenient().when(post.getCommentCount()).thenReturn(0);
		lenient().when(post.getStatus()).thenReturn(status);
		return post;
	}

	@Test
	void createPostSucceedsForFreeCategory() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost saved = mockPost(10L, user, BoardStatus.ACTIVE);
		given(userRepository.getReferenceById(1L)).willReturn(user);
		given(boardPostRepository.save(any(BoardPost.class))).willReturn(saved);

		BoardPostResponse response = boardPostService.createPost(
				1L, new BoardPostCreateRequest(BoardCategory.FREE, "제목", "내용", null, null)
		);

		assertThat(response.id()).isEqualTo(10L);
		verify(plantJournalRepository, never()).findOwnedActive(anyLong(), anyLong());
	}

	@Test
	void createPostFailsWhenNonAdminWritesNotice() {
		User user = mockUser(1L, UserRole.USER);
		given(userRepository.getReferenceById(1L)).willReturn(user);

		assertThatThrownBy(() -> boardPostService.createPost(
				1L, new BoardPostCreateRequest(BoardCategory.NOTICE, "제목", "내용", null, null)
		)).isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_NOTICE_FORBIDDEN);
	}

	@Test
	void createPostSucceedsWhenAdminWritesNotice() {
		User admin = mockUser(1L, UserRole.ADMIN);
		BoardPost saved = mockPost(10L, admin, BoardStatus.ACTIVE);
		given(userRepository.getReferenceById(1L)).willReturn(admin);
		given(boardPostRepository.save(any(BoardPost.class))).willReturn(saved);

		BoardPostResponse response = boardPostService.createPost(
				1L, new BoardPostCreateRequest(BoardCategory.NOTICE, "공지", "내용", null, null)
		);

		assertThat(response.id()).isEqualTo(10L);
	}

	@Test
	void createPostFailsWhenPlantQnaHasNoJournalId() {
		User user = mockUser(1L, UserRole.USER);
		given(userRepository.getReferenceById(1L)).willReturn(user);

		assertThatThrownBy(() -> boardPostService.createPost(
				1L, new BoardPostCreateRequest(BoardCategory.PLANT_QNA, "제목", "내용", null, null)
		)).isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_JOURNAL_REQUIRED);
	}

	@Test
	void createPostFailsWhenJournalNotOwnedByRequester() {
		User user = mockUser(1L, UserRole.USER);
		given(userRepository.getReferenceById(1L)).willReturn(user);
		given(plantJournalRepository.findOwnedActive(99L, 1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> boardPostService.createPost(
				1L, new BoardPostCreateRequest(BoardCategory.PLANT_QNA, "제목", "내용", 99L, null)
		)).isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_JOURNAL_NOT_OWNED);
	}

	@Test
	void createPostSucceedsWhenJournalOwnedByRequester() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost saved = mockPost(10L, user, BoardStatus.ACTIVE);
		given(userRepository.getReferenceById(1L)).willReturn(user);
		given(plantJournalRepository.findOwnedActive(99L, 1L)).willReturn(Optional.of(mock(PlantJournal.class)));
		given(boardPostRepository.save(any(BoardPost.class))).willReturn(saved);

		BoardPostResponse response = boardPostService.createPost(
				1L, new BoardPostCreateRequest(BoardCategory.PLANT_QNA, "제목", "내용", 99L, null)
		);

		assertThat(response.id()).isEqualTo(10L);
	}

	@Test
	void getPostsMapsRepositoryPage() {
		Pageable pageable = PageRequest.of(0, 10);
		User user = mockUser(1L, UserRole.USER);
		Page<BoardPost> page = new PageImpl<>(List.of(mockPost(10L, user, BoardStatus.ACTIVE)));
		given(boardPostRepository.search(BoardStatus.ACTIVE, BoardCategory.FREE, null, "TITLE_CONTENT", pageable)).willReturn(page);

		Page<BoardPostResponse> result = boardPostService.getPosts(BoardCategory.FREE, null, null, pageable, null);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).id()).isEqualTo(10L);
	}

	@Test
	void getPostsMarksLikedByMeForCurrentUser() {
		Pageable pageable = PageRequest.of(0, 10);
		User user = mockUser(1L, UserRole.USER);
		Page<BoardPost> page = new PageImpl<>(List.of(mockPost(10L, user, BoardStatus.ACTIVE)));
		given(boardPostRepository.search(BoardStatus.ACTIVE, BoardCategory.FREE, null, "TITLE_CONTENT", pageable)).willReturn(page);
		given(boardPostLikeRepository.findLikedPostIds(2L, List.of(10L))).willReturn(List.of(10L));

		Page<BoardPostResponse> result = boardPostService.getPosts(BoardCategory.FREE, null, null, pageable, 2L);

		assertThat(result.getContent().get(0).likedByMe()).isTrue();
	}

	@Test
	void getPostReturnsActivePost() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));
		given(boardPostViewRepository.existsByPostIdAndIpAddress(10L, "1.2.3.4")).willReturn(false);

		BoardPostResponse response = boardPostService.getPost(10L, null, "1.2.3.4");

		assertThat(response.id()).isEqualTo(10L);
		assertThat(response.likedByMe()).isFalse();
		verify(post).incrementViewCount();
	}

	@Test
	void getPostDoesNotIncrementViewCountForRepeatIp() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));
		given(boardPostViewRepository.existsByPostIdAndIpAddress(10L, "1.2.3.4")).willReturn(true);

		boardPostService.getPost(10L, null, "1.2.3.4");

		verify(post, never()).incrementViewCount();
	}

	@Test
	void getPostReflectsLikedByMeForCurrentUser() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));
		given(boardPostLikeRepository.existsByPostIdAndUserId(10L, 2L)).willReturn(true);
		given(boardPostViewRepository.existsByPostIdAndIpAddress(10L, "1.2.3.4")).willReturn(false);

		BoardPostResponse response = boardPostService.getPost(10L, 2L, "1.2.3.4");

		assertThat(response.likedByMe()).isTrue();
	}

	@Test
	void getPostFailsWhenHidden() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.HIDDEN);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));

		assertThatThrownBy(() -> boardPostService.getPost(10L, null, "1.2.3.4"))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_POST_NOT_FOUND);
		verify(post, never()).incrementViewCount();
	}

	@Test
	void getPostFailsWhenNotFound() {
		given(boardPostRepository.findByIdWithUser(404L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> boardPostService.getPost(404L, null, "1.2.3.4"))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_POST_NOT_FOUND);
	}

	@Test
	void getLinkedJournalReturnsSnapshotRegardlessOfViewer() {
		User author = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, author, BoardStatus.ACTIVE);
		lenient().when(post.getJournalId()).thenReturn(77L);
		PlantJournalResponse snapshot = mock(PlantJournalResponse.class);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));
		given(plantJournalService.getPublicSnapshot(77L)).willReturn(snapshot);

		PlantJournalResponse result = boardPostService.getLinkedJournal(10L);

		assertThat(result).isSameAs(snapshot);
	}

	@Test
	void getLinkedJournalFailsWhenPostHasNoJournal() {
		User author = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, author, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));

		assertThatThrownBy(() -> boardPostService.getLinkedJournal(10L))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_JOURNAL_NOT_LINKED);
	}

	@Test
	void getLinkedJournalFailsWhenPostNotFound() {
		given(boardPostRepository.findByIdWithUser(404L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> boardPostService.getLinkedJournal(404L))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_POST_NOT_FOUND);
	}

	@Test
	void updatePostSucceedsForOwner() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));

		BoardPostResponse response =
				boardPostService.updatePost(1L, 10L, new BoardPostUpdateRequest("새 제목", "새 내용", null));

		verify(post).update("새 제목", "새 내용");
		assertThat(response.id()).isEqualTo(10L);
	}

	@Test
	void updatePostFailsWhenNotOwner() {
		User owner = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, owner, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));

		assertThatThrownBy(() -> boardPostService.updatePost(2L, 10L, new BoardPostUpdateRequest("새 제목", "새 내용", null)))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_POST_NOT_OWNED);
	}

	@Test
	void deletePostHidesPostForOwner() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));

		boardPostService.deletePost(1L, 10L);

		verify(post).hide(eq(BoardHiddenBy.AUTHOR), any());
	}

	@Test
	void deletePostFailsWhenNotOwner() {
		User owner = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, owner, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));

		assertThatThrownBy(() -> boardPostService.deletePost(2L, 10L))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_POST_NOT_OWNED);
	}

	@Test
	void getMyPostsMapsRepositoryPage() {
		Pageable pageable = PageRequest.of(0, 10);
		User user = mockUser(1L, UserRole.USER);
		Page<BoardPost> page = new PageImpl<>(List.of(mockPost(10L, user, BoardStatus.HIDDEN)));
		given(boardPostRepository.findAllByUserId(1L, pageable)).willReturn(page);

		Page<BoardPostResponse> result = boardPostService.getMyPosts(1L, pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).id()).isEqualTo(10L);
	}

	@Test
	void adminHidePostHidesActivePost() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));

		boardPostService.adminHidePost(10L);

		verify(post).hide(eq(BoardHiddenBy.ADMIN), any());
	}

	@Test
	void likePostSucceedsWhenNotAlreadyLiked() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));
		given(boardPostLikeRepository.existsByPostIdAndUserId(10L, 1L)).willReturn(false);
		given(userRepository.getReferenceById(1L)).willReturn(user);

		boardPostService.likePost(1L, 10L);

		verify(post).incrementLikeCount();
	}

	@Test
	void likePostFailsWhenAlreadyLiked() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		given(boardPostRepository.findByIdWithUser(10L)).willReturn(Optional.of(post));
		given(boardPostLikeRepository.existsByPostIdAndUserId(10L, 1L)).willReturn(true);

		assertThatThrownBy(() -> boardPostService.likePost(1L, 10L))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_ALREADY_LIKED);
	}

	@Test
	void unlikePostSucceedsWhenLiked() {
		User user = mockUser(1L, UserRole.USER);
		BoardPost post = mockPost(10L, user, BoardStatus.ACTIVE);
		BoardPostLike like = mock(BoardPostLike.class);
		given(boardPostLikeRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.of(like));
		given(boardPostRepository.getReferenceById(10L)).willReturn(post);

		boardPostService.unlikePost(1L, 10L);

		verify(boardPostLikeRepository).delete(like);
		verify(post).decrementLikeCount();
	}

	@Test
	void unlikePostFailsWhenNotLiked() {
		given(boardPostLikeRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> boardPostService.unlikePost(1L, 10L))
				.isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_LIKE_NOT_FOUND);
	}
}
