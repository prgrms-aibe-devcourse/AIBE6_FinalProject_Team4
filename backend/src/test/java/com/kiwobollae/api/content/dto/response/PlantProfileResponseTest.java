package com.kiwobollae.api.content.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// journalRewardGrantedToday는 LocalDate.now(KST)를 직접 호출해 판정하므로, 시스템 시계를 흉내내는
// 대신 "지금"과 "어제"라는 상대적인 기준으로만 검증한다 — 자정 근처(초 단위)에 도는 극히 드문
// 경우가 아니면 테스트 실행 시각과 무관하게 항상 같은 결과가 나온다.
class PlantProfileResponseTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Test
	void journalRewardGrantedTodayIsTrueWhenGrantedAtIsToday() {
		PlantProfile profile = profile(LocalDateTime.now(KST));

		PlantProfileResponse response = PlantProfileResponse.from(profile);

		assertThat(response.journalRewardGrantedToday()).isTrue();
	}

	@Test
	void journalRewardGrantedTodayIsFalseWhenGrantedAtIsYesterday() {
		PlantProfile profile = profile(LocalDateTime.now(KST).minusDays(1));

		PlantProfileResponse response = PlantProfileResponse.from(profile);

		assertThat(response.journalRewardGrantedToday()).isFalse();
	}

	@Test
	void journalRewardGrantedTodayIsFalseWhenNeverGranted() {
		PlantProfile profile = profile(null);

		PlantProfileResponse response = PlantProfileResponse.from(profile);

		assertThat(response.journalRewardGrantedToday()).isFalse();
	}

	private PlantProfile profile(LocalDateTime journalRewardGrantedAt) {
		User user = User.builder().build();
		ReflectionTestUtils.setField(user, "id", 7L);
		PlantSpecies species = PlantSpecies.builder().name("바질").build();
		ReflectionTestUtils.setField(species, "id", 3L);
		PlantProfile profile = PlantProfile.builder()
				.user(user)
				.species(species)
				.plantName("바질이")
				.startDate(LocalDate.now(KST))
				.status(PlantStatus.GROWING)
				.createdAt(LocalDateTime.now(KST))
				.journalRewardGrantedAt(journalRewardGrantedAt)
				.build();
		ReflectionTestUtils.setField(profile, "id", 21L);
		return profile;
	}
}
