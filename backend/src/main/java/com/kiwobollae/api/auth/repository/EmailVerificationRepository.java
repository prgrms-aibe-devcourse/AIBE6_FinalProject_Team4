package com.kiwobollae.api.auth.repository;

import com.kiwobollae.api.auth.entity.EmailVerification;
import com.kiwobollae.api.auth.entity.enums.EmailVerificationPurpose;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

	Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDesc(
			String email, EmailVerificationPurpose purpose);
}
