package com.kiwobollae.api.board.repository;

import com.kiwobollae.api.board.entity.BoardComment;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

	@Query("select c from BoardComment c join fetch c.user "
			+ "where c.post.id = :postId and c.status = :status order by c.createdAt asc")
	List<BoardComment> findAllByPostIdAndStatus(@Param("postId") Long postId, @Param("status") BoardStatus status);

	// 부모가 숨김 처리돼도 ACTIVE 상태인 대댓글은 트리에서 계속 보여야 하므로, 상태 필터 없이
	// 전부 가져온다 — HIDDEN 댓글은 내용만 프론트에서 "삭제된 댓글입니다"로 대체 표시한다.
	@Query("select c from BoardComment c join fetch c.user "
			+ "where c.post.id = :postId order by c.createdAt asc")
	List<BoardComment> findAllByPostId(@Param("postId") Long postId);

	@Query("select c from BoardComment c join fetch c.user where c.id = :id")
	Optional<BoardComment> findByIdWithUser(@Param("id") Long id);

	// 마이페이지 "내가 쓴 댓글" — 본인 글이라 상태(ACTIVE/HIDDEN) 필터 없이 전부 보여준다.
	@Query(value = "select c from BoardComment c join fetch c.user join fetch c.post where c.user.id = :userId",
			countQuery = "select count(c) from BoardComment c where c.user.id = :userId")
	Page<BoardComment> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

	// BoardPostRepository의 카운터들과 동일하게 Lost Update를 피하기 위한 원자적 증감.
	@Modifying
	@Query("update BoardComment c set c.likeCount = c.likeCount + 1 where c.id = :id")
	int incrementLikeCount(@Param("id") Long id);

	@Modifying
	@Query("update BoardComment c set c.likeCount = c.likeCount - 1 where c.id = :id")
	int decrementLikeCount(@Param("id") Long id);
}
