package com.kiwobollae.api.content.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.content.entity.enums.RewardStatus;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 식물 프로필이 일지 작성완료 보상을 획득했음을 기록하는 로그.
 *
 * <p>point 도메인과의 경계: content(일지) 도메인은 이 로그를 남기고 {@link #rewardStatus} 마커만
 * 관리한다. 사용자 지갑에 대한 실제 포인트 지급/회수는 이 로그를 읽는 point 도메인이 수행한다.
 * 여기의 rewardStatus는 point 도메인이 정식 플로우를 맡기 전까지의 임시 값으로 취급한다.
 */
@Getter
@Entity
@Table(name = "journal_completion_log", indexes = {
		// UNIQUE(plant_profile_id, completion_date): 같은 식물이 같은 날 중복 완료되는 것을 DB에서 차단.
		// "1식물 1회(영구)" 비즈니스 규칙은 서비스 계층에서 판정해, 정책이 바뀌어도 스키마 마이그레이션이
		// 없도록 한다. plant_profile_id는 nullable(소유 프로필 하드 삭제 시 NULL로 끊김)이고, MySQL은
		// unique 인덱스에서 NULL 다중 허용이라 끊긴 로그끼리는 충돌하지 않는다.
		@Index(name = "uq_journal_completion_log_plant_profile_id_completion_date",
				columnList = "plant_profile_id, completion_date", unique = true)
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalCompletionLog extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_profile_id")
	private PlantProfile plantProfile;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_journal_id")
	private PlantJournal plantJournal;

	@Column(name = "completion_date", nullable = false)
	private LocalDate completionDate;

	// content 도메인이 관리하는 보상 마커. 실제 지급/회수는 point 도메인이 수행한다.
	// 지급(REWARDED)과 회수(REVOKED)를 모두 기록해 포인트 이력의 전체 추적을 남긴다.
	@Enumerated(EnumType.STRING)
	@Column(name = "reward_status", nullable = false, length = 20)
	private RewardStatus rewardStatus;

	@Column(name = "plant_nickname_snapshot", length = 50)
	private String plantNicknameSnapshot;

	// 특정 식물의 첫 완료 일지에서 생성된다. 초기 상태는 임시로 REWARDED이며,
	// point 도메인이 이 로그를 읽어 실제 포인트를 지급한다.
	public static JournalCompletionLog create(User user, PlantProfile plantProfile,
			PlantJournal plantJournal, LocalDate completionDate) {
		return JournalCompletionLog.builder()
				.user(user)
				.plantProfile(plantProfile)
				.plantJournal(plantJournal)
				.completionDate(completionDate)
				.rewardStatus(RewardStatus.REWARDED)
				.build();
	}

	// 마커를 REVOKED로 전환한다(예: 완료 일지를 같은 날 삭제한 경우). 지갑에 대한 실제 포인트
	// 회수는 point 도메인이 담당한다.
	public void markRevoked() {
		this.rewardStatus = RewardStatus.REVOKED;
	}
}
