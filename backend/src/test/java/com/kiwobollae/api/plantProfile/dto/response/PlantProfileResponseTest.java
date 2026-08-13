package com.kiwobollae.api.plantProfile.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.plantProfile.dto.response.PlantProfileResponse;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlantProfileResponseTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Test
	void fromMapsSpeciesCareGuide() {
		PlantProfile profile = profile();

		PlantProfileResponse response = PlantProfileResponse.from(profile);

		assertThat(response.careGuide()).isEqualTo("정중앙에서 촬영하면 나중에 타임랩스가 예뻐요.");
	}

	private PlantProfile profile() {
		User user = User.builder().build();
		ReflectionTestUtils.setField(user, "id", 7L);
		PlantSpecies species = PlantSpecies.builder()
				.name("바질")
				.careGuide("정중앙에서 촬영하면 나중에 타임랩스가 예뻐요.")
				.build();
		ReflectionTestUtils.setField(species, "id", 3L);
		PlantProfile profile = PlantProfile.builder()
				.user(user)
				.species(species)
				.plantName("바질이")
				.startDate(LocalDate.now(KST))
				.status(PlantStatus.GROWING)
				.createdAt(LocalDateTime.now(KST))
				.build();
		ReflectionTestUtils.setField(profile, "id", 21L);
		return profile;
	}
}
