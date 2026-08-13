package com.kiwobollae.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.board.service.BoardCommentService;
import com.kiwobollae.api.board.service.BoardPostService;
import com.kiwobollae.api.journal.service.PlantJournalService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.report.dto.request.ReportRequest;
import com.kiwobollae.api.report.dto.response.ReportResponse;
import com.kiwobollae.api.report.entity.Report;
import com.kiwobollae.api.report.entity.enums.ReportTargetType;
import com.kiwobollae.api.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

	@Mock private ReportRepository reportRepository;
	@Mock private UserRepository userRepository;
	@Mock private PlantJournalService plantJournalService;
	@Mock private BoardPostService boardPostService;
	@Mock private BoardCommentService boardCommentService;
	@InjectMocks private ReportService reportService;

	@Test
	void createReportSucceedsForActivePost() {
		User reporter = mock(User.class);
		lenient().when(reporter.getId()).thenReturn(1L);
		lenient().when(reporter.getName()).thenReturn("초록이");
		Report saved = mock(Report.class);
		lenient().when(saved.getReporter()).thenReturn(reporter);
		lenient().when(saved.getTargetType()).thenReturn(ReportTargetType.POST);
		lenient().when(saved.getTargetId()).thenReturn(10L);
		given(boardPostService.existsActive(10L)).willReturn(true);
		given(userRepository.getReferenceById(1L)).willReturn(reporter);
		given(reportRepository.save(any(Report.class))).willReturn(saved);

		ReportResponse response = reportService.createReport(
				1L, new ReportRequest(ReportTargetType.POST, 10L, "부적절한 내용")
		);

		assertThat(response).isNotNull();
	}

	@Test
	void createReportFailsWhenPostNotActive() {
		given(boardPostService.existsActive(10L)).willReturn(false);

		assertThatThrownBy(() -> reportService.createReport(
				1L, new ReportRequest(ReportTargetType.POST, 10L, "부적절한 내용")
		)).isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_POST_NOT_FOUND);
	}

	@Test
	void createReportFailsWhenCommentNotActive() {
		given(boardCommentService.existsActive(20L)).willReturn(false);

		assertThatThrownBy(() -> reportService.createReport(
				1L, new ReportRequest(ReportTargetType.COMMENT, 20L, "부적절한 내용")
		)).isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.BOARD_COMMENT_NOT_FOUND);
	}

	@Test
	void createReportFailsForUnsupportedTargetType() {
		assertThatThrownBy(() -> reportService.createReport(
				1L, new ReportRequest(ReportTargetType.USER, 30L, "부적절한 내용")
		)).isInstanceOf(BusinessException.class)
				.extracting(ex -> ((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
	}
}
