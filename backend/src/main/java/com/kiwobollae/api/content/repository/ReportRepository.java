package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}
