package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
}
