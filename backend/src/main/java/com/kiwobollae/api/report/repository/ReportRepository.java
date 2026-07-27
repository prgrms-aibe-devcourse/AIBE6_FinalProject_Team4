package com.kiwobollae.api.report.repository;

import com.kiwobollae.api.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}
