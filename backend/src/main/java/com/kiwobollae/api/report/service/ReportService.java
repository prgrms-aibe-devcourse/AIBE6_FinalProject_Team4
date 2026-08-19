package com.kiwobollae.api.report.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.board.service.BoardCommentService;
import com.kiwobollae.api.board.service.BoardPostService;
import com.kiwobollae.api.journal.service.PlantJournalService;
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
	private final BoardPostService boardPostService;
	private final BoardCommentService boardCommentService;

	@Transactional
	public ReportResponse createReport(Long userId, ReportRequest request) {
		validateTarget(request.targetType(), request.targetId());
		if (reportRepository.existsWithStatus(userId, request.targetType(), request.targetId(), ReportStatus.PENDING)) {
			throw new BusinessException(ErrorCode.REPORT_DUPLICATE_PENDING);
		}
		User reporter = userRepository.getReferenceById(userId);
		Report saved = reportRepository.save(
				Report.create(reporter, request.targetType(), request.targetId(), request.reason()));
		return ReportResponse.from(saved);
	}

	private void validateTarget(ReportTargetType targetType, Long targetId) {
		switch (targetType) {
			case JOURNAL:
				if (!plantJournalService.existsActive(targetId)) {
					throw new BusinessException(ErrorCode.JOURNAL_NOT_FOUND);
				}
				break;
			case POST:
				if (!boardPostService.existsActive(targetId)) {
					throw new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND);
				}
				break;
			case COMMENT:
				if (!boardCommentService.existsActive(targetId)) {
					throw new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND);
				}
				break;
			case USER:
			default:
				throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "현재는 일지·게시글·댓글만 신고할 수 있습니다.");
		}
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

	// 신고 검토용 콘텐츠 상세 조회(일지/댓글) 앞단에서 쓰는 인가 체크. 대상이 실제로 신고된 적
	// 있는지(상태 무관) 확인하지 않으면, ADMIN은 ID만 알면 신고와 무관한 비공개 일지도 열람할 수
	// 있게 된다 — getPublicSnapshot/getForAdmin류가 원래 "호출부가 정당한 접근인지 검증한다"는
	// 전제로 만들어졌기 때문에, 그 검증을 여기서 대신 해준다.
	public boolean existsReportForTarget(ReportTargetType targetType, Long targetId) {
		return reportRepository.existsByTargetTypeAndTargetId(targetType, targetId);
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
