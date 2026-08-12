package com.kiwobollae.api.commerce.gacha;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawItemRepository;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.journal.dto.request.JournalImageRequest;
import com.kiwobollae.api.journal.dto.request.PlantJournalRequest;
import com.kiwobollae.api.journal.dto.response.PlantJournalCreateResponse;
import com.kiwobollae.api.journal.service.PlantJournalService;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.species.repository.PlantSpeciesRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "local"})
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_gacha_test"
          + "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.seed.gacha.enabled=true",
      "app.seed.charge-product.enabled=false",
      "app.seed.product.enabled=false",
      "app.seed.card.enabled=false"
    })
class GachaJournalRewardMySqlIntegrationTest {

  @Autowired private PlantJournalService plantJournalService;
  @Autowired private UserRepository userRepository;
  @Autowired private PlantSpeciesRepository plantSpeciesRepository;
  @Autowired private PlantProfileRepository plantProfileRepository;
  @Autowired private TradingCardRepository tradingCardRepository;
  @Autowired private GachaDrawRepository gachaDrawRepository;
  @Autowired private GachaDrawItemRepository gachaDrawItemRepository;
  @Autowired private UserCardCollectionRepository collectionRepository;

  @Test
  void firstJournalRewardCreatesAndCompletesOneFiveCardDraw() {
    User user = userRepository.findByEmail("test@test.com").orElseThrow();
    PlantSpecies species =
        plantSpeciesRepository.save(
            PlantSpecies.builder().name("가챠 QA 식물").category("QA").careGuide("통합 테스트용").build());
    PlantProfile firstProfile =
        plantProfileRepository.save(
            PlantProfile.create(user, species, "첫 번째 QA 식물", LocalDate.now().minusDays(3), null));
    PlantProfile secondProfile =
        plantProfileRepository.save(
            PlantProfile.create(user, species, "두 번째 QA 식물", LocalDate.now().minusDays(2), null));

    PlantJournalCreateResponse first =
        plantJournalService.createJournal(
            user.getId(),
            request(firstProfile.getId(), "/journal-demo/photo-1.svg", "1".repeat(64)));
    PlantJournalCreateResponse second =
        plantJournalService.createJournal(
            user.getId(),
            request(secondProfile.getId(), "/journal-demo/photo-2.svg", "2".repeat(64)));

    assertThat(first.journal().gachaReward().granted()).isTrue();
    assertThat(first.journal().gachaReward().drawId()).isNotNull();
    assertThat(second.journal().gachaReward().granted()).isFalse();
    assertThat(second.journal().gachaReward().drawId())
        .isEqualTo(first.journal().gachaReward().drawId());
    assertThat(tradingCardRepository.count()).isEqualTo(43);
    assertThat(gachaDrawRepository.count()).isEqualTo(1);

    GachaDraw draw = awaitCompleted(first.journal().gachaReward().drawId());
    assertThat(draw.getStatus()).isEqualTo(GachaDrawStatus.COMPLETED);
    assertThat(gachaDrawItemRepository.findAllByGachaDraw_IdOrderByDrawSeqAsc(draw.getId()))
        .hasSize(5);
    assertThat(
            collectionRepository.findAllByUser_Id(user.getId()).stream()
                .mapToInt(collection -> collection.getOwnedCount())
                .sum())
        .isEqualTo(5);
  }

  private GachaDraw awaitCompleted(Long drawId) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    GachaDraw draw = gachaDrawRepository.findById(drawId).orElseThrow();
    while (draw.getStatus() != GachaDrawStatus.COMPLETED && System.nanoTime() < deadline) {
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
      draw = gachaDrawRepository.findById(drawId).orElseThrow();
    }
    return draw;
  }

  private PlantJournalRequest request(Long profileId, String imageUrl, String imageHash) {
    return new PlantJournalRequest(
        profileId, "실제 가챠 지급 통합 테스트", List.of(new JournalImageRequest(imageUrl, imageHash, true)));
  }
}
