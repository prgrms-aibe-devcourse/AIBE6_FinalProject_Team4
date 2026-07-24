package com.kiwobollae.api.content.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.content.entity.enums.InquiryCategory;
import com.kiwobollae.api.content.entity.enums.InquiryStatus;
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
@Table(name = "inquiry", indexes = {
		@Index(name = "idx_inquiry_user_id_created_at", columnList = "user_id, created_at"),
		@Index(name = "idx_inquiry_status_created_at", columnList = "status, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Inquiry extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InquiryCategory category;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(name = "inquiry_content", nullable = false, length = 1000)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InquiryStatus status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "answer_content", length = 1000)
	private String answerContent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "admin_id")
	private User answerAdmin;

	@Column(name = "answer_created_at")
	private LocalDateTime answeredAt;
}
