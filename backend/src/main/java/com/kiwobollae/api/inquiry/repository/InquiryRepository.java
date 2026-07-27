package com.kiwobollae.api.inquiry.repository;

import com.kiwobollae.api.inquiry.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
}
