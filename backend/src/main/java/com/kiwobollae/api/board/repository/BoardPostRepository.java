package com.kiwobollae.api.board.repository;

import com.kiwobollae.api.board.entity.BoardPost;
import com.kiwobollae.api.board.entity.enums.BoardCategory;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

	// join fetch로 User를 함께 가져와 목록 조회 시 건별 지연 로딩(N+1)을 막는다.
	// keyword는 searchType(제목+내용/제목/내용/글쓴이/댓글)에 따라 매칭된다(대소문자 무시).
	// keyword가 null/빈 값이면 검색 조건 전체를 무시한다. searchType은 :keyword가 있을 때만 의미가 있으므로
	// keyword가 null이면 :searchType 값과 무관하게 항상 참인 첫 번째 or 절에서 短서킷된다.
	// excludeCategory는 프론트가 공지를 별도로 고정 노출하는 화면(카테고리 필터 없는 "전체" 탭)에서
	// 그 카테고리(NOTICE)를 이 페이지네이션 결과 자체에서 아예 빼기 위한 것 — 클라이언트에서 받은
	// 뒤에 걸러내면 페이지당 개수/전체 개수/전체 페이지 수가 실제 표시 목록과 안 맞게 된다.
	@Query(value = "select p from BoardPost p join fetch p.user "
			+ "where p.status = :status and (:category is null or p.category = :category) "
			+ "and (:excludeCategory is null or p.category <> :excludeCategory) "
			+ "and (:keyword is null"
			+ " or (:searchType = 'TITLE_CONTENT' and (lower(p.title) like lower(concat('%', :keyword, '%')) "
			+ "or lower(p.content) like lower(concat('%', :keyword, '%'))))"
			+ " or (:searchType = 'TITLE' and lower(p.title) like lower(concat('%', :keyword, '%')))"
			+ " or (:searchType = 'CONTENT' and lower(p.content) like lower(concat('%', :keyword, '%')))"
			+ " or (:searchType = 'AUTHOR' and lower(p.user.nickname) like lower(concat('%', :keyword, '%')))"
			+ " or (:searchType = 'COMMENT' and exists (select 1 from BoardComment c "
			+ "where c.post = p and c.status = com.kiwobollae.api.board.entity.enums.BoardStatus.ACTIVE "
			+ "and lower(c.content) like lower(concat('%', :keyword, '%')))))",
			countQuery = "select count(p) from BoardPost p "
					+ "where p.status = :status and (:category is null or p.category = :category) "
					+ "and (:excludeCategory is null or p.category <> :excludeCategory) "
					+ "and (:keyword is null"
					+ " or (:searchType = 'TITLE_CONTENT' and (lower(p.title) like lower(concat('%', :keyword, '%')) "
					+ "or lower(p.content) like lower(concat('%', :keyword, '%'))))"
					+ " or (:searchType = 'TITLE' and lower(p.title) like lower(concat('%', :keyword, '%')))"
					+ " or (:searchType = 'CONTENT' and lower(p.content) like lower(concat('%', :keyword, '%')))"
					+ " or (:searchType = 'AUTHOR' and lower(p.user.nickname) like lower(concat('%', :keyword, '%')))"
					+ " or (:searchType = 'COMMENT' and exists (select 1 from BoardComment c "
					+ "where c.post = p and c.status = com.kiwobollae.api.board.entity.enums.BoardStatus.ACTIVE "
					+ "and lower(c.content) like lower(concat('%', :keyword, '%')))))")
	Page<BoardPost> search(
			@Param("status") BoardStatus status,
			@Param("category") BoardCategory category,
			@Param("excludeCategory") BoardCategory excludeCategory,
			@Param("keyword") String keyword,
			@Param("searchType") String searchType,
			Pageable pageable
	);

	@Query("select p from BoardPost p join fetch p.user where p.id = :id")
	Optional<BoardPost> findByIdWithUser(@Param("id") Long id);

	// 마이페이지 "내가 쓴 게시글" — 본인 글이라 상태(ACTIVE/HIDDEN) 필터 없이 전부 보여준다.
	@Query(value = "select p from BoardPost p join fetch p.user where p.user.id = :userId",
			countQuery = "select count(p) from BoardPost p where p.user.id = :userId")
	Page<BoardPost> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

	// product.stock 등 다른 카운터들과 동일하게, 메모리에 로드한 값 + 1을 dirty checking으로
	// 반영하는 방식(Lost Update 위험)을 피하고 DB에서 직접 원자적으로 증감한다.
	@Modifying
	@Query("update BoardPost p set p.likeCount = p.likeCount + 1 where p.id = :id")
	int incrementLikeCount(@Param("id") Long id);

	@Modifying
	@Query("update BoardPost p set p.likeCount = p.likeCount - 1 where p.id = :id")
	int decrementLikeCount(@Param("id") Long id);

	@Modifying
	@Query("update BoardPost p set p.viewCount = p.viewCount + 1 where p.id = :id")
	int incrementViewCount(@Param("id") Long id);

	@Modifying
	@Query("update BoardPost p set p.commentCount = p.commentCount + 1 where p.id = :id")
	int incrementCommentCount(@Param("id") Long id);

	@Modifying
	@Query("update BoardPost p set p.commentCount = p.commentCount - 1 where p.id = :id")
	int decrementCommentCount(@Param("id") Long id);
}
