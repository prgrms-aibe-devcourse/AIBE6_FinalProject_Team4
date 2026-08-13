package com.kiwobollae.api.journal.repository;

import com.kiwobollae.api.journal.entity.DailyJournalReward;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyJournalRewardRepository extends JpaRepository<DailyJournalReward, Long> {

	/** MySQL 유일 제약을 이용해 계정·KST 날짜별 최초 보상 요청을 선점한다. 반환 행 수는 사용하지 않는다. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			insert into daily_journal_rewards
				(user_id, reward_date, journal_id, reward_amount, rewarded_at)
			values (:userId, :rewardDate, :journalId, :rewardAmount, :rewardedAt)
			on duplicate key update id = id
			""", nativeQuery = true)
	int claim(
			@Param("userId") Long userId,
			@Param("rewardDate") LocalDate rewardDate,
			@Param("journalId") Long journalId,
			@Param("rewardAmount") long rewardAmount,
			@Param("rewardedAt") LocalDateTime rewardedAt
	);

	boolean existsByUser_IdAndRewardDate(Long userId, LocalDate rewardDate);

	Optional<DailyJournalReward> findByUser_IdAndRewardDate(Long userId, LocalDate rewardDate);
}
