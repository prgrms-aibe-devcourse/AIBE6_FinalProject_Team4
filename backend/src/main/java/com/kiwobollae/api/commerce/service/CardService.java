package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.repository.CardPurchaseLogRepository;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.UserCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CardService {

	private final CardRepository cardRepository;
	private final UserCardRepository userCardRepository;
	private final CardPurchaseLogRepository cardPurchaseLogRepository;
}
