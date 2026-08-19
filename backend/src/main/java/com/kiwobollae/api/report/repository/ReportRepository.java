package com.kiwobollae.api.report.repository;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.report.entity.Report;
import com.kiwobollae.api.report.entity.enums.ReportStatus;
import com.kiwobollae.api.report.entity.enums.ReportTargetType;
import java.time.LocalDateTime;
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

	@Modifying
	@Query("update Report r set r.processedAdmin = :admin, r.actionType = :actionType, "
			+ "r.actionDetail = :actionDetail, r.processedAt = :now, r.status = :newStatus "
			+ "where r.id = :id and r.status = :expectedStatus")
	int updateStatusIfMatches(@Param("id") Long id, @Param("admin") User admin,
			@Param("actionType") String actionType, @Param("actionDetail") String actionDetail,
			@Param("now") LocalDateTime now, @Param("newStatus") ReportStatus newStatus,
			@Param("expectedStatus") ReportStatus expectedStatus);
}
