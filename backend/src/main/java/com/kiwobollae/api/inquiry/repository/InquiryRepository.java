package com.kiwobollae.api.inquiry.repository;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.inquiry.entity.Inquiry;
import com.kiwobollae.api.inquiry.entity.enums.InquiryStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

	@Query(value = "select i from Inquiry i left join fetch i.answerAdmin where i.user.id = :userId",
			countQuery = "select count(i) from Inquiry i where i.user.id = :userId")
	Page<Inquiry> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

	@Query("select i from Inquiry i left join fetch i.answerAdmin where i.id = :id and i.user.id = :userId")
	Optional<Inquiry> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	@Query("select i from Inquiry i left join fetch i.answerAdmin where i.id = :id")
	Optional<Inquiry> findByIdWithAnswerAdmin(@Param("id") Long id);

	// 관리자 전체 목록 조회, status는 null이면 전체. 응답에 작성자 이름(userName)이 매 항목마다
	// 필요해 i.user도 함께 join fetch한다 — 여러 사용자의 문의가 섞여 있어 findAllByUserId와
	// 달리 첫 번째 레벨 캐시로는 안 걸러지는 진짜 N+1이 생긴다.
	@Query(value = "select i from Inquiry i left join fetch i.answerAdmin join fetch i.user "
			+ "where (:status is null or i.status = :status)",
			countQuery = "select count(i) from Inquiry i where (:status is null or i.status = :status)")
	Page<Inquiry> search(@Param("status") InquiryStatus status, Pageable pageable);

	// OPEN인 경우에만 원자적으로 답변·상태를 반영하는 조건부 UPDATE (동시 답변 경쟁 조건 방지)
	@Modifying
	@Query("update Inquiry i set i.answerAdmin = :admin, i.answerContent = :answerContent, "
			+ "i.answeredAt = :now, i.status = :newStatus where i.id = :id and i.status = :expectedStatus")
	int answerIfMatches(@Param("id") Long id, @Param("admin") User admin,
			@Param("answerContent") String answerContent, @Param("now") LocalDateTime now,
			@Param("newStatus") InquiryStatus newStatus, @Param("expectedStatus") InquiryStatus expectedStatus);
}
