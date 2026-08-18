package com.kiwobollae.api.board.repository;

import com.kiwobollae.api.board.entity.BoardPostImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardPostImageRepository extends JpaRepository<BoardPostImage, Long> {

	List<BoardPostImage> findByPostIdOrderBySortOrderAsc(Long postId);

	@Query("select i from BoardPostImage i where i.post.id in :postIds order by i.sortOrder asc")
	List<BoardPostImage> findByPostIdIn(@Param("postIds") Collection<Long> postIds);

	@Modifying
	@Query("delete from BoardPostImage i where i.post.id = :postId")
	int deleteByPostId(@Param("postId") Long postId);

	// S3 정리 전 안전 장치 — 이미지 URL이 어떤 게시글에 아직 쓰이고 있는지 확인한다.
	boolean existsByImageUrl(String imageUrl);
}
