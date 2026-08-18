package com.kiwobollae.api.board.repository;

import com.kiwobollae.api.board.entity.BoardCommentLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardCommentLikeRepository extends JpaRepository<BoardCommentLike, Long> {

	boolean existsByCommentIdAndUserId(Long commentId, Long userId);

	Optional<BoardCommentLike> findByCommentIdAndUserId(Long commentId, Long userId);

	// 댓글 목록 조회에서 댓글마다 좋아요 여부를 매번 쿼리하지 않도록 한 번에 뽑아온다.
	@Query("select l.comment.id from BoardCommentLike l where l.user.id = :userId and l.comment.id in :commentIds")
	List<Long> findLikedCommentIds(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);
}
