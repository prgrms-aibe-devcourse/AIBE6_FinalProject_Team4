package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InquiryService {

	private final InquiryRepository inquiryRepository;
}
