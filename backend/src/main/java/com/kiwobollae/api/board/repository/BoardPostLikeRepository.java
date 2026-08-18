package com.kiwobollae.api.board.repository;

import com.kiwobollae.api.board.entity.BoardPostLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardPostLikeRepository extends JpaRepository<BoardPostLike, Long> {

	boolean existsByPostIdAndUserId(Long postId, Long userId);

	Optional<BoardPostLike> findByPostIdAndUserId(Long postId, Long userId);

	// 목록 조회에서 게시글마다 좋아요 여부를 매번 쿼리하지 않도록, 현재 페이지의 게시글 id들에 대해
	// 한 번에 좋아요한 게시글 id만 뽑아온다.
	@Query("select l.post.id from BoardPostLike l where l.user.id = :userId and l.post.id in :postIds")
	List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
