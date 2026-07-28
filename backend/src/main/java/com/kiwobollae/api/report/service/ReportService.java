package com.kiwobollae.api.report.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.service.PlantJournalService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.report.dto.request.ReportActionRequest;
import com.kiwobollae.api.report.dto.request.ReportRequest;
import com.kiwobollae.api.report.dto.response.ReportResponse;
import com.kiwobollae.api.report.entity.Report;
import com.kiwobollae.api.report.entity.enums.ReportStatus;
import com.kiwobollae.api.report.entity.enums.ReportTargetType;
import com.kiwobollae.api.report.repository.ReportRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

	private final ReportRepository reportRepository;
	private final UserRepository userRepository;
	private final PlantJournalService plantJournalService;

	@Transactional
	public ReportResponse createReport(Long userId, ReportRequest request) {
		if (request.targetType() != ReportTargetType.JOURNAL) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "현재는 성장 일지만 신고할 수 있습니다.");
		}
		if (!plantJournalService.existsActive(request.targetId())) {
			throw new BusinessException(ErrorCode.JOURNAL_NOT_FOUND);
		}
		if (reportRepository.existsWithStatus(userId, request.targetType(), request.targetId(), ReportStatus.PENDING)) {
			throw new BusinessException(ErrorCode.REPORT_DUPLICATE_PENDING);
		}
		User reporter = userRepository.getReferenceById(userId);
		Report saved = reportRepository.save(
				Report.create(reporter, request.targetType(), request.targetId(), request.reason()));
		return ReportResponse.from(saved);
	}

	public Page<ReportResponse> getMyReports(Long userId, Pageable pageable) {
		return reportRepository.findAllByReporterId(userId, pageable).map(ReportResponse::from);
	}

	public ReportResponse getReport(Long userId, Long reportId) {
		Report report = reportRepository.findByIdAndReporterId(reportId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
		return ReportResponse.from(report);
	}

	public Page<ReportResponse> getReportsForAdmin(ReportStatus status, Pageable pageable) {
		return reportRepository.search(status, pageable).map(ReportResponse::from);
	}

	@Transactional
	public ReportResponse completeReport(Long adminId, Long reportId, ReportActionRequest request) {
		return processReport(adminId, reportId, request, ReportStatus.COMPLETED);
	}

	@Transactional
	public ReportResponse rejectReport(Long adminId, Long reportId, ReportActionRequest request) {
		return processReport(adminId, reportId, request, ReportStatus.REJECTED);
	}

	// PENDING인 경우에만 원자적으로 상태를 바꾸는 조건부 UPDATE. 두 관리자가 동시에 같은 건을
	// 처리해도 WHERE절의 status 조건 때문에 둘 중 하나만 실제로 반영되고, 나머지는 0건 갱신으로
	// 감지된다(check-then-act 경쟁 조건 없음).
	private ReportResponse processReport(Long adminId, Long reportId, ReportActionRequest request,
			ReportStatus newStatus) {
		User admin = userRepository.getReferenceById(adminId);
		int updated = reportRepository.updateStatusIfMatches(reportId, admin, request.actionType(),
				request.actionDetail(), LocalDateTime.now(), newStatus, ReportStatus.PENDING);
		if (updated == 0) {
			if (!reportRepository.existsById(reportId)) {
				throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
			}
			throw new BusinessException(ErrorCode.REPORT_INVALID_STATE);
		}
		Report report = reportRepository.findByIdWithReporter(reportId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
		return ReportResponse.from(report);
	}
}
