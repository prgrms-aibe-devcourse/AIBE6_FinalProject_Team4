package com.kiwobollae.api.report.repository;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.report.entity.Report;
import com.kiwobollae.api.report.entity.enums.ReportStatus;
import com.kiwobollae.api.report.entity.enums.ReportTargetType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

	@Query(value = "select r from Report r left join fetch r.processedAdmin where r.reporter.id = :reporterId",
			countQuery = "select count(r) from Report r where r.reporter.id = :reporterId")
	Page<Report> findAllByReporterId(@Param("reporterId") Long reporterId, Pageable pageable);

	@Query("select r from Report r join fetch r.reporter left join fetch r.processedAdmin "
			+ "where r.id = :id and r.reporter.id = :reporterId")
	Optional<Report> findByIdAndReporterId(@Param("id") Long id, @Param("reporterId") Long reporterId);

	@Query("select r from Report r join fetch r.reporter left join fetch r.processedAdmin where r.id = :id")
	Optional<Report> findByIdWithReporter(@Param("id") Long id);

	@Query(value = "select r from Report r join fetch r.reporter left join fetch r.processedAdmin "
			+ "where (:status is null or r.status = :status)",
			countQuery = "select count(r) from Report r where (:status is null or r.status = :status)")
	Page<Report> search(@Param("status") ReportStatus status, Pageable pageable);

	@Query("select (count(r) > 0) from Report r where r.reporter.id = :reporterId "
			+ "and r.targetType = :targetType and r.targetId = :targetId and r.status = :status")
	boolean existsWithStatus(@Param("reporterId") Long reporterId, @Param("targetType") ReportTargetType targetType,
			@Param("targetId") Long targetId, @Param("status") ReportStatus status);

	boolean existsByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);

	// Report.targetId는 대상 유형(JOURNAL/POST/COMMENT/USER)에 따라 다른 테이블을 가리키는
	// 폴리모픽 참조라 FK 매핑이 없다(Report.java 주석 참고) — "이 유저를 겨냥한 신고가 몇 건인지"를
	// 구하려면 POST/COMMENT/JOURNAL 신고를 각 콘텐츠의 작성자로 직접 조인해서 풀어야 한다.
	// 관리자 회원 목록 한 페이지(최대 100명) 분량을 한 번에 집계하기 위한 배치 쿼리.
	@Query(value = "select reported_user_id as userId, count(*) as reportCount from ("
			+ "  select target_id as reported_user_id from report where target_type = 'USER'"
			+ "  union all"
			+ "  select bp.user_id as reported_user_id from report r "
			+ "    join board_posts bp on r.target_id = bp.id where r.target_type = 'POST'"
			+ "  union all"
			+ "  select bc.user_id as reported_user_id from report r "
			+ "    join board_comments bc on r.target_id = bc.id where r.target_type = 'COMMENT'"
			+ "  union all"
			+ "  select pj.user_id as reported_user_id from report r "
			+ "    join plant_journals pj on r.target_id = pj.id where r.target_type = 'JOURNAL'"
			+ ") x where reported_user_id in (:userIds) group by reported_user_id",
			nativeQuery = true)
	List<UserReportCountProjection> countReportsAgainstUsers(@Param("userIds") List<Long> userIds);

	// 특정 유저를 겨냥한 신고 id 목록 — 위와 같은 이유로 네이티브 쿼리로 직접 조인한다.
	// id만 뽑아 온 뒤 findByIdInAndStatus로 엔티티(+ reporter fetch)를 다시 조회해서 쓴다.
	@Query(value = "select r.id from report r where "
			+ "(r.target_type = 'USER' and r.target_id = :userId) "
			+ "or (r.target_type = 'POST' and r.target_id in (select id from board_posts where user_id = :userId)) "
			+ "or (r.target_type = 'COMMENT' and r.target_id in (select id from board_comments where user_id = :userId)) "
			+ "or (r.target_type = 'JOURNAL' and r.target_id in (select id from plant_journals where user_id = :userId))",
			nativeQuery = true)
	List<Long> findReportIdsAgainstUser(@Param("userId") Long userId);

	@Query(value = "select r from Report r join fetch r.reporter left join fetch r.processedAdmin "
			+ "where r.id in :ids and (:status is null or r.status = :status)",
			countQuery = "select count(r) from Report r where r.id in :ids and (:status is null or r.status = :status)")
	Page<Report> findByIdInAndStatus(@Param("ids") List<Long> ids, @Param("status") ReportStatus status, Pageable pageable);

	@Modifying
	@Query("update Report r set r.processedAdmin = :admin, r.actionType = :actionType, "
			+ "r.actionDetail = :actionDetail, r.processedAt = :now, r.status = :newStatus "
			+ "where r.id = :id and r.status = :expectedStatus")
	int updateStatusIfMatches(@Param("id") Long id, @Param("admin") User admin,
			@Param("actionType") String actionType, @Param("actionDetail") String actionDetail,
			@Param("now") LocalDateTime now, @Param("newStatus") ReportStatus newStatus,
			@Param("expectedStatus") ReportStatus expectedStatus);
}
