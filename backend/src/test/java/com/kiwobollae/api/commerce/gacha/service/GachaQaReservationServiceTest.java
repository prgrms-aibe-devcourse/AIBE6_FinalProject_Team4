package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaQaBatchDrawResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaQaDrawResponse;
import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class GachaQaReservationServiceTest {

  @Test
  void reservesIndependentQaDrawWithCommonIdempotencyKey() {
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    GachaQaReservationService service =
        new GachaQaReservationService(
            idempotencyService, drawRepository, userRepository, eventPublisher);
    IdempotencyKey key = mock(IdempotencyKey.class);
    User user = mock(User.class);

    when(key.getId()).thenReturn(501L);
    when(userRepository.getReferenceById(7L)).thenReturn(user);
    when(idempotencyService.start(eq(7L), eq("GACHA_QA_TEST"), eq("qa-key"), anyString()))
        .thenReturn(new IdempotencyExecution(key, false));
    when(drawRepository.saveAndFlush(any(GachaDraw.class)))
        .thenAnswer(
            invocation -> {
              GachaDraw draw = invocation.getArgument(0);
              ReflectionTestUtils.setField(draw, "id", 71L);
              return draw;
            });

    GachaQaDrawResponse response = service.reserve(7L, "qa-key");

    ArgumentCaptor<GachaDraw> drawCaptor = ArgumentCaptor.forClass(GachaDraw.class);
    verify(drawRepository).saveAndFlush(drawCaptor.capture());
    GachaDraw saved = drawCaptor.getValue();
    assertThat(saved.getUser()).isSameAs(user);
    assertThat(saved.getSourceType()).isEqualTo(GachaSourceType.ADMIN);
    assertThat(saved.getSourceId()).isEqualTo(501L);
    assertThat(saved.getDrawCount()).isEqualTo(5);
    assertThat(response).isEqualTo(new GachaQaDrawResponse(71L, GachaDrawStatus.PENDING));
    verify(idempotencyService).succeed(key, 201, "{\"drawId\":71}", "GACHA_DRAW", 71L);
    verify(eventPublisher).publishEvent(new GachaRewardCreatedEvent(71L));
  }

  @Test
  void reservesOneHundredIndependentPacksInOneQaRequest() {
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    GachaQaReservationService service =
        new GachaQaReservationService(
            idempotencyService, drawRepository, userRepository, eventPublisher);
    User user = mock(User.class);
    AtomicLong keySequence = new AtomicLong(1_000L);
    AtomicLong drawSequence = new AtomicLong(2_000L);

    when(userRepository.getReferenceById(7L)).thenReturn(user);
    when(idempotencyService.start(eq(7L), eq("GACHA_QA_TEST"), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              IdempotencyKey key = mock(IdempotencyKey.class);
              when(key.getId()).thenReturn(keySequence.getAndIncrement());
              return new IdempotencyExecution(key, false);
            });
    when(drawRepository.saveAndFlush(any(GachaDraw.class)))
        .thenAnswer(
            invocation -> {
              GachaDraw draw = invocation.getArgument(0);
              ReflectionTestUtils.setField(draw, "id", drawSequence.getAndIncrement());
              return draw;
            });

    GachaQaBatchDrawResponse response = service.reserveBatch(7L, "batch-key", 100);

    assertThat(response.packCount()).isEqualTo(100);
    assertThat(response.drawIds()).hasSize(100).startsWith(2_000L).endsWith(2_099L);
    verify(drawRepository, times(100)).saveAndFlush(any(GachaDraw.class));
    verify(eventPublisher, times(100)).publishEvent(any(GachaRewardCreatedEvent.class));
  }
}
