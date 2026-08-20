package com.kiwobollae.api.auth.service;

import com.kiwobollae.api.auth.dto.response.AdminUserSummaryResponse;
import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.AdminUserQueryRepository;
import com.kiwobollae.api.report.repository.ReportRepository;
import com.kiwobollae.api.report.repository.UserReportCountProjection;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserQueryService {

	private static final int MAX_PAGE_SIZE = 100;

	private final AdminUserQueryRepository adminUserQueryRepository;
	private final ReportRepository reportRepository;

	public Page<AdminUserSummaryResponse> search(
			String keyword,
			UserStatus status,
			Pageable pageable
	) {
		String normalizedKeyword = normalizeKeyword(keyword);
		Pageable safePageable = PageRequest.of(
				pageable.getPageNumber(),
				Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
		);
		Page<User> users = adminUserQueryRepository.findAll(matches(normalizedKeyword, status), safePageable);
		Map<Long, Long> reportCountsByUserId = countReportsByUserId(users.getContent());
		return users.map(user ->
				AdminUserSummaryResponse.from(user, reportCountsByUserId.getOrDefault(user.getId(), 0L)));
	}

	// report.target_id는 FK가 아닌 폴리모픽 참조라(신고 유형별로 다른 테이블을 가리킴), 이 유저를
	// 겨냥한 신고 수는 별도 네이티브 집계 쿼리로만 구할 수 있다 — 빈 id 목록으로 IN ()을 그대로
	// 나가면 DB에 따라 문법 오류가 나므로 미리 걸러낸다.
	private Map<Long, Long> countReportsByUserId(List<User> users) {
		List<Long> userIds = users.stream().map(User::getId).toList();
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return reportRepository.countReportsAgainstUsers(userIds).stream()
				.collect(Collectors.toMap(UserReportCountProjection::getUserId, UserReportCountProjection::getReportCount));
	}

	private Specification<User> matches(String keyword, UserStatus status) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> conditions = new ArrayList<>();
			if (status != null) {
				conditions.add(criteriaBuilder.equal(root.get("status"), status));
			}
			if (!keyword.isEmpty()) {
				String likeKeyword = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
				List<Predicate> keywordConditions = new ArrayList<>();
				keywordConditions.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), likeKeyword));
				keywordConditions.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("nickname")), likeKeyword));
				keywordConditions.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), likeKeyword));
				parseUserId(keyword).ifPresent(userId ->
						keywordConditions.add(criteriaBuilder.equal(root.get("id"), userId)));
				conditions.add(criteriaBuilder.or(keywordConditions.toArray(Predicate[]::new)));
			}
			return criteriaBuilder.and(conditions.toArray(Predicate[]::new));
		};
	}

	private String normalizeKeyword(String keyword) {
		return keyword == null ? "" : keyword.trim();
	}

	private java.util.Optional<Long> parseUserId(String keyword) {
		try {
			long userId = Long.parseLong(keyword);
			return userId > 0 ? java.util.Optional.of(userId) : java.util.Optional.empty();
		} catch (NumberFormatException exception) {
			return java.util.Optional.empty();
		}
	}
}
