package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.response.GachaPackProductQuote;
import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseRequest;
import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.commerce.service.ProductService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.service.WalletService;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class GachaPackPurchaseServiceTest {

  @Test
  void purchasesUpToOneHundredPointPacksAndReservesIndependentDraws() {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            userRepository,
            eventPublisher,
            new ObjectMapper());
    IdempotencyKey purchaseKey = mock(IdempotencyKey.class);
    User user = mock(User.class);
    AtomicLong packKeySequence = new AtomicLong(601L);
    AtomicLong drawSequence = new AtomicLong(701L);

    when(purchaseKey.getId()).thenReturn(501L);
    when(idempotencyService.start(
            eq(7L), eq("GACHA_PACK_PURCHASE"), eq("purchase-key"), anyString()))
        .thenReturn(new IdempotencyExecution(purchaseKey, false));
    when(idempotencyService.start(eq(7L), eq("GACHA_PACK_PURCHASE_ITEM"), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              IdempotencyKey key = mock(IdempotencyKey.class);
              when(key.getId()).thenReturn(packKeySequence.getAndIncrement());
              return new IdempotencyExecution(key, false);
            });
    when(productService.getActiveGachaPack(9L))
        .thenReturn(new GachaPackProductQuote(9L, "시즌 1 가챠 카드팩", 100L, 30));
    when(walletService.deductForGachaPurchase(7L, 200L, 501L))
        .thenReturn(new PointDeductionResult(150L, 50L, 800L));
    when(userRepository.getReferenceById(7L)).thenReturn(user);
    when(drawRepository.saveAndFlush(any(GachaDraw.class)))
        .thenAnswer(
            invocation -> {
              GachaDraw draw = invocation.getArgument(0);
              ReflectionTestUtils.setField(draw, "id", drawSequence.getAndIncrement());
              return draw;
            });

    var response = service.purchase(7L, "purchase-key", new GachaPackPurchaseRequest(9L, 2));

    assertThat(response.quantity()).isEqualTo(2);
    assertThat(response.unitPoint()).isEqualTo(100L);
    assertThat(response.totalPoint()).isEqualTo(200L);
    assertThat(response.usedFreePoint()).isEqualTo(150L);
    assertThat(response.usedPaidPoint()).isEqualTo(50L);
    assertThat(response.drawIds()).containsExactly(701L, 702L);

    ArgumentCaptor<GachaDraw> drawCaptor = ArgumentCaptor.forClass(GachaDraw.class);
    verify(drawRepository, times(2)).saveAndFlush(drawCaptor.capture());
    assertThat(drawCaptor.getAllValues())
        .allSatisfy(
            draw -> {
              assertThat(draw.getUser()).isSameAs(user);
              assertThat(draw.getSourceType()).isEqualTo(GachaSourceType.PURCHASE);
              assertThat(draw.getDrawCount()).isEqualTo(5);
            });
    assertThat(drawCaptor.getAllValues())
        .extracting(GachaDraw::getSourceId)
        .containsExactly(601L, 602L);
    verify(eventPublisher, times(2)).publishEvent(any(GachaRewardCreatedEvent.class));
    verify(idempotencyService)
        .succeed(eq(purchaseKey), eq(200), anyString(), eq("GACHA_PACK_PURCHASE"), eq(501L));
  }

  @Test
  void rejectsMoreThanOneHundredPacksBeforePointDeduction() {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            mock(GachaDrawRepository.class),
            mock(UserRepository.class),
            mock(ApplicationEventPublisher.class),
            new ObjectMapper());

    assertThatThrownBy(
            () -> service.purchase(7L, "purchase-key", new GachaPackPurchaseRequest(9L, 31)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

    verify(idempotencyService, never()).start(any(), anyString(), anyString(), anyString());
    verify(walletService, never()).deductForGachaPurchase(any(), anyLong(), any());
  }

  @Test
  void acceptsExactlyOneHundredPacks() {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            userRepository,
            eventPublisher,
            new ObjectMapper());
    IdempotencyKey purchaseKey = mock(IdempotencyKey.class);
    AtomicLong packKeySequence = new AtomicLong(1_000L);
    AtomicLong drawSequence = new AtomicLong(2_000L);

    when(purchaseKey.getId()).thenReturn(501L);
    when(idempotencyService.start(eq(7L), eq("GACHA_PACK_PURCHASE"), eq("max-key"), anyString()))
        .thenReturn(new IdempotencyExecution(purchaseKey, false));
    when(idempotencyService.start(eq(7L), eq("GACHA_PACK_PURCHASE_ITEM"), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              IdempotencyKey key = mock(IdempotencyKey.class);
              when(key.getId()).thenReturn(packKeySequence.getAndIncrement());
              return new IdempotencyExecution(key, false);
            });
    when(productService.getActiveGachaPack(9L))
        .thenReturn(new GachaPackProductQuote(9L, "시즌 1 가챠 카드팩", 100L, 30));
    when(walletService.deductForGachaPurchase(7L, 3_000L, 501L))
        .thenReturn(new PointDeductionResult(1_000L, 2_000L, 0L));
    when(userRepository.getReferenceById(7L)).thenReturn(mock(User.class));
    when(drawRepository.saveAndFlush(any(GachaDraw.class)))
        .thenAnswer(
            invocation -> {
              GachaDraw draw = invocation.getArgument(0);
              ReflectionTestUtils.setField(draw, "id", drawSequence.getAndIncrement());
              return draw;
            });

    var response = service.purchase(7L, "max-key", new GachaPackPurchaseRequest(9L, 30));

    assertThat(response.quantity()).isEqualTo(30);
    assertThat(response.totalPoint()).isEqualTo(3_000L);
    assertThat(response.drawIds()).hasSize(30).startsWith(2_000L).endsWith(2_029L);
    verify(drawRepository, times(30)).saveAndFlush(any(GachaDraw.class));
    verify(eventPublisher, times(30)).publishEvent(any(GachaRewardCreatedEvent.class));
  }
}
