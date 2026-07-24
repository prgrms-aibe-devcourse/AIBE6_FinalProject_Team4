package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.Inquiry;
import com.kiwobollae.api.content.entity.enums.InquiryCategory;
import com.kiwobollae.api.content.entity.enums.InquiryStatus;
import java.time.LocalDateTime;

public record InquiryResponse(
		Long id,
		Long userId,
		InquiryCategory category,
		String title,
		String content,
		InquiryStatus status,
		LocalDateTime createdAt,
		String answerContent,
		Long answerAdminId,
		String answerAdminName,
		LocalDateTime answeredAt
) {
	public static InquiryResponse from(Inquiry inquiry) {
		return new InquiryResponse(
				inquiry.getId(),
				inquiry.getUser().getId(),
				inquiry.getCategory(),
				inquiry.getTitle(),
				inquiry.getContent(),
				inquiry.getStatus(),
				inquiry.getCreatedAt(),
				inquiry.getAnswerContent(),
				inquiry.getAnswerAdmin() != null ? inquiry.getAnswerAdmin().getId() : null,
				inquiry.getAnswerAdmin() != null ? inquiry.getAnswerAdmin().getName() : null,
				inquiry.getAnsweredAt()
		);
	}
}
