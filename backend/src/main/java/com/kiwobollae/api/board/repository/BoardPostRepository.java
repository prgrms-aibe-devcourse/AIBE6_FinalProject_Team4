package com.kiwobollae.api.board.repository;

import com.kiwobollae.api.board.entity.BoardPost;
import com.kiwobollae.api.board.entity.enums.BoardCategory;
import com.kiwobollae.api.board.entity.enums.BoardStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

	// join fetch로 User를 함께 가져와 목록 조회 시 건별 지연 로딩(N+1)을 막는다.
	// keyword는 searchType(제목+내용/제목/내용/글쓴이/댓글)에 따라 매칭된다(대소문자 무시).
	// keyword가 null/빈 값이면 검색 조건 전체를 무시한다. searchType은 :keyword가 있을 때만 의미가 있으므로
	// keyword가 null이면 :searchType 값과 무관하게 항상 참인 첫 번째 or 절에서 短서킷된다.
	@Query(value = "select p from BoardPost p join fetch p.user "
			+ "where p.status = :status and (:category is null or p.category = :category) "
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
}
