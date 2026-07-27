package com.kiwobollae.api.report.repository;

import com.kiwobollae.api.report.entity.Report;
import com.kiwobollae.api.report.entity.enums.ReportStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

	// 내 신고 목록: reporter는 항상 본인이라 N건 중 첫 접근 1회만 로딩되면 충분해 join fetch 생략
	@Query(value = "select r from Report r left join fetch r.processedAdmin where r.reporter.id = :reporterId",
			countQuery = "select count(r) from Report r where r.reporter.id = :reporterId")
	Page<Report> findAllByReporterId(@Param("reporterId") Long reporterId, Pageable pageable);

	@Query("select r from Report r left join fetch r.processedAdmin "
			+ "where r.id = :id and r.reporter.id = :reporterId")
	Optional<Report> findByIdAndReporterId(@Param("id") Long id, @Param("reporterId") Long reporterId);

	// 관리자 전체 목록 조회: 신고자가 행마다 달라 N+1 방지를 위해 reporter도 join fetch
	@Query(value = "select r from Report r join fetch r.reporter left join fetch r.processedAdmin "
			+ "where (:status is null or r.status = :status)",
			countQuery = "select count(r) from Report r where (:status is null or r.status = :status)")
	Page<Report> search(@Param("status") ReportStatus status, Pageable pageable);
}
