package com.kiwobollae.api.inquiry.repository;

import com.kiwobollae.api.inquiry.entity.Inquiry;
import com.kiwobollae.api.inquiry.entity.enums.InquiryStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

	@Query(value = "select i from Inquiry i left join fetch i.answerAdmin where i.user.id = :userId",
			countQuery = "select count(i) from Inquiry i where i.user.id = :userId")
	Page<Inquiry> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

	@Query("select i from Inquiry i left join fetch i.answerAdmin where i.id = :id and i.user.id = :userId")
	Optional<Inquiry> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	// 관리자 전체 목록 조회, status는 null이면 전체
	@Query(value = "select i from Inquiry i left join fetch i.answerAdmin "
			+ "where (:status is null or i.status = :status)",
			countQuery = "select count(i) from Inquiry i where (:status is null or i.status = :status)")
	Page<Inquiry> search(@Param("status") InquiryStatus status, Pageable pageable);
}
