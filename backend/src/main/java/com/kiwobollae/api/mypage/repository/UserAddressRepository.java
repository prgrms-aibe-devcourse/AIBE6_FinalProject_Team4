package com.kiwobollae.api.mypage.repository;

import com.kiwobollae.api.mypage.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
}
