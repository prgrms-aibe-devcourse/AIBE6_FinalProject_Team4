package com.kiwobollae.api.auth.repository;

import com.kiwobollae.api.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 관리자 회원 검색 전용 읽기 Repository. 기존 인증 Repository와 쓰기 흐름은 변경하지 않는다. */
public interface AdminUserQueryRepository
		extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
}
