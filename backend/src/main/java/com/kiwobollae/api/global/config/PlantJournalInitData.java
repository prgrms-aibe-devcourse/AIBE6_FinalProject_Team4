package com.kiwobollae.api.global.config;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.entity.JournalImage;
import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only sample journal entries so /journals isn't empty for the InitData
 * test user — useful while journal image upload (and thus real journal creation
 * end-to-end) is still blocked. Uses placeholder image URLs the same way
 * ProductInitData does for product images.
 *
 * <p>Depends on InitData (users, @Order(1)) and PlantProfileInitData (plant
 * profiles, @Order(3)) having already run; skips silently if either is missing.
 * Attaches journals to whichever profiles actually exist rather than hardcoding
 * nicknames, since profiles can be deleted independently of this seed running.
 *
 * <p>Disable without changing code by setting {@code app.seed.journal.enabled=false}.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed.journal", name = "enabled", havingValue = "true")
@Order(4)
@RequiredArgsConstructor
public class PlantJournalInitData implements ApplicationRunner {

	private static final String IMAGE_BASE_URL = "https://placehold.co/800x800/E8F3D8/4B7A1E?text=";

	private static final List<String> SAMPLE_CONTENTS = List.of(
			"오늘도 잎이 한 뼘 더 자랐어요. 아침마다 조금씩 커지는 게 신기해요.",
			"물을 듬뿍 줬더니 훨씬 생기가 도네요. 내일은 지지대를 세워줘야겠어요.",
			"새잎이 세 장이나 났어요. 곧 첫 수확 할 수 있을 것 같아요."
	);

	private final UserRepository userRepository;
	private final PlantProfileRepository plantProfileRepository;
	private final PlantJournalRepository plantJournalRepository;
	private final JournalImageRepository journalImageRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (plantJournalRepository.count() > 0) {
			return;
		}

		User testUser = userRepository.findByEmail("test@test.com").orElse(null);
		if (testUser == null) {
			return;
		}

		List<PlantProfile> profiles = plantProfileRepository.findAllByUserId(testUser.getId());
		if (profiles.isEmpty()) {
			return;
		}

		int seedIndex = 0;
		for (PlantProfile profile : profiles) {
			for (int entry = 0; entry < 2; entry++) {
				LocalDate writtenDate = LocalDate.now().minusDays(entry);
				String content = SAMPLE_CONTENTS.get(seedIndex % SAMPLE_CONTENTS.size());

				PlantJournal journal = plantJournalRepository.save(
						PlantJournal.create(testUser, profile, content, writtenDate));

				JournalImage image = JournalImage.create(
						journal, testUser,
						IMAGE_BASE_URL + profile.getPlantName(),
						"seed-" + seedIndex,
						true,
						writtenDate);
				journalImageRepository.save(image);

				seedIndex++;
			}
		}
	}
}
