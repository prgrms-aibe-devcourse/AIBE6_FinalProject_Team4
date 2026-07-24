package com.kiwobollae.api.point.service;

import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletService {

	private final WalletRepository walletRepository;
	private final PointTransactionRepository pointTransactionRepository;
}
