package com.kiwobollae.api.auth.repository;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	boolean existsByNicknameAndIdNot(String nickname, Long id);

	// JwtAuthenticationFilter가 매 요청마다 "이 유저 지금도 ACTIVE인가"를 확인하는 데 쓴다.
	// User 전체를 로드하지 않고 idx_users_status 인덱스로 바로 확인하는 가벼운 쿼리.
	boolean existsByIdAndStatus(Long id, UserStatus status);

	// UserStatusCache가 캐시 미스일 때 한 번만 부르는 조회 — User 전체가 아니라 status 컬럼만
	// 읽어온다. 유저가 존재하지 않으면(탈퇴 후 물리 삭제 등) 빈 값을 반환한다.
	@Query("select u.status from User u where u.id = :id")
	Optional<UserStatus> findStatusById(@Param("id") Long id);

	// 사용자 행에 쓰기 락을 걸어, 같은 사용자에 대한 동시 요청을(예: 배송지 등록 시
	// count-then-insert) 직렬화해야 하는 곳에서 쓴다. 다른 사용자 행에는 영향 없다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);
}
