package com.kiwobollae.api.mypage.service;

import com.kiwobollae.api.mypage.repository.UserAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAddressService {

	private final UserAddressRepository userAddressRepository;
}
