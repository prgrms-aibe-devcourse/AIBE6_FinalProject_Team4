package com.kiwobollae.api.inquiry.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.inquiry.dto.request.InquiryAnswerRequest;
import com.kiwobollae.api.inquiry.dto.request.InquiryRequest;
import com.kiwobollae.api.inquiry.dto.response.InquiryResponse;
import com.kiwobollae.api.inquiry.entity.Inquiry;
import com.kiwobollae.api.inquiry.entity.enums.InquiryStatus;
import com.kiwobollae.api.inquiry.repository.InquiryRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

	private final InquiryRepository inquiryRepository;
	private final UserRepository userRepository;

	@Transactional
	public InquiryResponse createInquiry(Long userId, InquiryRequest request) {
		User user = userRepository.getReferenceById(userId);
		Inquiry saved = inquiryRepository.save(
				Inquiry.create(user, request.category(), request.title(), request.content()));
		return InquiryResponse.from(saved);
	}

	public Page<InquiryResponse> getMyInquiries(Long userId, Pageable pageable) {
		return inquiryRepository.findAllByUserId(userId, pageable).map(InquiryResponse::from);
	}

	public InquiryResponse getInquiry(Long userId, Long inquiryId) {
		Inquiry inquiry = inquiryRepository.findByIdAndUserId(inquiryId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
		return InquiryResponse.from(inquiry);
	}

	public Page<InquiryResponse> getInquiriesForAdmin(InquiryStatus status, Pageable pageable) {
		return inquiryRepository.search(status, pageable).map(InquiryResponse::from);
	}

	// OPEN인 경우에만 원자적으로 답변을 반영한다. 두 관리자가 동시에 같은 문의에 답변해도
	// WHERE절의 status 조건 때문에 둘 중 하나만 실제로 반영되고, 나머지는 0건 갱신으로 감지된다.
	@Transactional
	public InquiryResponse answerInquiry(Long adminId, Long inquiryId, InquiryAnswerRequest request) {
		User admin = userRepository.getReferenceById(adminId);
		int updated = inquiryRepository.answerIfMatches(inquiryId, admin, request.answerContent(),
				LocalDateTime.now(), InquiryStatus.ANSWERED, InquiryStatus.OPEN);
		if (updated == 0) {
			if (!inquiryRepository.existsById(inquiryId)) {
				throw new BusinessException(ErrorCode.INQUIRY_NOT_FOUND);
			}
			throw new BusinessException(ErrorCode.INQUIRY_INVALID_STATE);
		}
		Inquiry inquiry = inquiryRepository.findByIdWithAnswerAdmin(inquiryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
		return InquiryResponse.from(inquiry);
	}
}
