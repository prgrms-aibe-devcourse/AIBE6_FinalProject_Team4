package com.kiwobollae.api.auth.service;

import com.kiwobollae.api.auth.dto.response.AdminUserSummaryResponse;
import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.AdminUserQueryRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
		return adminUserQueryRepository
				.findAll(matches(normalizedKeyword, status), safePageable)
				.map(AdminUserSummaryResponse::from);
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
