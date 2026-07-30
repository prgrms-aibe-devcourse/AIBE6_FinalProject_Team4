package com.kiwobollae.api.commerce.gacha.config;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed.gacha-qa", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LocalGachaQaInitData {

  private static final String QA_USER_EMAIL = "test@test.com";

  private final UserRepository userRepository;
  private final PlantSpeciesRepository plantSpeciesRepository;
  private final PlantProfileRepository plantProfileRepository;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void seedQaPlant() {
    userRepository
        .findByEmail(QA_USER_EMAIL)
        .filter(user -> plantProfileRepository.findAllByUserId(user.getId()).isEmpty())
        .ifPresent(
            user -> {
              PlantSpecies species =
                  plantSpeciesRepository.save(
                      PlantSpecies.builder()
                          .name("가챠 QA 상추")
                          .category("QA")
                          .careGuide("로컬 가챠 화면 검증용 식물입니다.")
                          .build());
              plantProfileRepository.save(
                  PlantProfile.create(
                      user,
                      species,
                      "카드싹",
                      LocalDate.now().minusDays(7),
                      "/journal-demo/photo-1.svg"));
            });
  }
}
