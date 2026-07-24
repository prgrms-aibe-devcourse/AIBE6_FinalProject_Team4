package com.kiwobollae.api.content.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.content.entity.enums.ReportStatus;
import com.kiwobollae.api.content.entity.enums.ReportTargetType;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "report", indexes = {
		@Index(name = "idx_report_reporter_target", columnList = "reporter_id, target_type, target_id", unique = true),
		@Index(name = "idx_report_status_created_at", columnList = "status, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Report extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reporter_id", nullable = false)
	private User reporter;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20)
	private ReportTargetType targetType;

	/** Polymorphic reference (JOURNAL/USER) — no FK mapping. */
	@Column(name = "target_id", nullable = false)
	private Long targetId;

	@Column(nullable = false, length = 500)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportStatus status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "action_type", length = 50)
	private String actionType;

	@Column(name = "action_detail", length = 500)
	private String actionDetail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "processed_admin_id")
	private User processedAdmin;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;
}
