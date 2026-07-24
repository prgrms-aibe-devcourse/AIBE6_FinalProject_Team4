package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.repository.JournalCompletionLogRepository;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlantJournalService {

	private final PlantJournalRepository plantJournalRepository;
	private final JournalCompletionLogRepository journalCompletionLogRepository;
}
