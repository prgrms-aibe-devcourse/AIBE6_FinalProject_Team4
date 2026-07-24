package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

	private final ReportRepository reportRepository;
}
