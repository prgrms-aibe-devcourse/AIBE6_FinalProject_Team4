package com.kiwobollae.api.mypage.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.mypage.dto.request.UserAddressRequest;
import com.kiwobollae.api.mypage.dto.response.UserAddressResponse;
import com.kiwobollae.api.mypage.entity.UserAddress;
import com.kiwobollae.api.mypage.repository.UserAddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAddressService {

	private static final int MAX_ADDRESSES_PER_USER = 5;

	private final UserAddressRepository userAddressRepository;
	private final UserRepository userRepository;

	public List<UserAddressResponse> getAddresses(Long userId) {
		return userAddressRepository.findAllByUser_IdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
				.map(UserAddressResponse::from)
				.toList();
	}

	@Transactional
	public UserAddressResponse createAddress(Long userId, UserAddressRequest request) {
		// 사용자 행에 쓰기 락을 걸어 같은 사용자의 동시 등록 요청을 직렬화한다 — 그러지
		// 않으면 count 체크와 insert 사이의 TOCTOU 틈으로 5개 제한이 뚫릴 수 있다.
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
		if (userAddressRepository.countByUser_Id(userId) >= MAX_ADDRESSES_PER_USER) {
			throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
		}
		UserAddress saved = userAddressRepository.save(UserAddress.create(
				user, request.receiverName(), request.receiverPhone(), request.zipCode(),
				request.address(), request.addressDetail(), request.isDefault()));
		// 새 배송지는 저장 후에야 id가 생기므로, 기본 지정은 저장 다음에 원자적으로 처리한다.
		if (request.isDefault()) {
			userAddressRepository.setOnlyDefault(userId, saved.getId());
		}
		return UserAddressResponse.from(saved);
	}

	@Transactional
	public UserAddressResponse updateAddress(Long userId, Long addressId, UserAddressRequest request) {
		UserAddress address = findOwnedAddress(userId, addressId);
		address.update(request.receiverName(), request.receiverPhone(), request.zipCode(),
				request.address(), request.addressDetail());
		if (request.isDefault()) {
			userAddressRepository.setOnlyDefault(userId, addressId);
			address.markDefault();
		} else if (address.getIsDefault()) {
			address.unmarkDefault();
			// 기본을 해제만 하고 끝내면 배송지가 남아 있는데도 기본이 하나도 없는
			// 상태가 되므로, 남은 배송지 중 가장 최근 것을 새 기본으로 승격시킨다.
			userAddressRepository.findFirstByUser_IdAndIdNotOrderByCreatedAtDesc(userId, addressId)
					.ifPresent(UserAddress::markDefault);
		}
		return UserAddressResponse.from(address);
	}

	@Transactional
	public void deleteAddress(Long userId, Long addressId) {
		UserAddress address = findOwnedAddress(userId, addressId);
		boolean wasDefault = address.getIsDefault();
		userAddressRepository.delete(address);
		// 기본 배송지를 지웠다면 남은 배송지 중 가장 최근 것을 새 기본으로 승격시킨다 —
		// 그러지 않으면 기본 배송지가 아예 없는 상태로 남는다.
		if (wasDefault) {
			userAddressRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
					.ifPresent(UserAddress::markDefault);
		}
	}

	@Transactional
	public UserAddressResponse setDefaultAddress(Long userId, Long addressId) {
		UserAddress address = findOwnedAddress(userId, addressId);
		if (!address.getIsDefault()) {
			userAddressRepository.setOnlyDefault(userId, addressId);
			address.markDefault();
		}
		return UserAddressResponse.from(address);
	}

	private UserAddress findOwnedAddress(Long userId, Long addressId) {
		return userAddressRepository.findByIdAndUser_Id(addressId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
	}
}
