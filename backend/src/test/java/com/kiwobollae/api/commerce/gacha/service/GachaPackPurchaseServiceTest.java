package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.response.GachaPackProductQuote;
import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseResponse;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class GachaPackPurchaseServiceTest {

  @Test
  void purchasesOnePackAndUsesPurchaseIdAsDrawSource() {
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

    when(purchaseKey.getId()).thenReturn(501L);
    when(idempotencyService.startWithCompatibleHash(
            eq(7L), eq("GACHA_PACK_PURCHASE"), eq("purchase-key"), anyString(), anyString()))
        .thenReturn(new IdempotencyExecution(purchaseKey, false));
    when(productService.getActiveGachaPack(9L))
        .thenReturn(new GachaPackProductQuote(9L, "시즌 1 가챠 카드팩", 100L, 1));
    when(walletService.deductForGachaPurchase(7L, 100L, 501L))
        .thenReturn(new PointDeductionResult(60L, 40L, 800L));
    when(userRepository.getReferenceById(7L)).thenReturn(user);
    when(drawRepository.saveAndFlush(any(GachaDraw.class)))
        .thenAnswer(
            invocation -> {
              GachaDraw draw = invocation.getArgument(0);
              ReflectionTestUtils.setField(draw, "id", 701L);
              return draw;
            });

    var response = service.purchase(7L, "purchase-key", new GachaPackPurchaseRequest(9L, 1, 100L));

    assertThat(response.quantity()).isEqualTo(1);
    assertThat(response.totalPoint()).isEqualTo(100L);
    assertThat(response.usedFreePoint()).isEqualTo(60L);
    assertThat(response.usedPaidPoint()).isEqualTo(40L);
    assertThat(response.drawIds()).containsExactly(701L);

    ArgumentCaptor<GachaDraw> drawCaptor = ArgumentCaptor.forClass(GachaDraw.class);
    verify(drawRepository).saveAndFlush(drawCaptor.capture());
    assertThat(drawCaptor.getValue().getUser()).isSameAs(user);
    assertThat(drawCaptor.getValue().getSourceType()).isEqualTo(GachaSourceType.PURCHASE);
    assertThat(drawCaptor.getValue().getSourceId()).isEqualTo(501L);
    verify(eventPublisher).publishEvent(any(GachaRewardCreatedEvent.class));
  }

  @Test
  void rejectsMultiplePacksBeforePointDeduction() {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            mock(UserRepository.class),
            mock(ApplicationEventPublisher.class),
            new ObjectMapper());

    assertThatThrownBy(
            () -> service.purchase(7L, "purchase-key", new GachaPackPurchaseRequest(9L, 2, 100L)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

    verify(idempotencyService, never()).start(any(), anyString(), anyString(), anyString());
    verify(idempotencyService, never())
        .startWithCompatibleHash(any(), anyString(), anyString(), anyString(), anyString());
    verify(walletService, never()).deductForGachaPurchase(any(), anyLong(), any());
  }

  @Test
  void rejectsChangedPriceBeforePointDeduction() {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            mock(UserRepository.class),
            mock(ApplicationEventPublisher.class),
            new ObjectMapper());
    IdempotencyKey purchaseKey = mock(IdempotencyKey.class);

    when(idempotencyService.startWithCompatibleHash(
            eq(7L), eq("GACHA_PACK_PURCHASE"), eq("purchase-key"), anyString(), anyString()))
        .thenReturn(new IdempotencyExecution(purchaseKey, false));
    when(productService.getActiveGachaPack(9L))
        .thenReturn(new GachaPackProductQuote(9L, "시즌 1 가챠 카드팩", 120L, 1));

    assertThatThrownBy(
            () -> service.purchase(7L, "purchase-key", new GachaPackPurchaseRequest(9L, 1, 100L)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.GACHA_PRODUCT_PRICE_CHANGED));

    verify(walletService, never()).deductForGachaPurchase(any(), anyLong(), any());
    verify(drawRepository, never()).saveAndFlush(any());
  }

  @Test
  void legacyRequestUsesCurrentServerPriceAndKeepsLegacyHash() {
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

    when(purchaseKey.getId()).thenReturn(502L);
    when(idempotencyService.start(7L, "GACHA_PACK_PURCHASE", "legacy-key", sha256("9:1")))
        .thenReturn(new IdempotencyExecution(purchaseKey, false));
    when(productService.getActiveGachaPack(9L))
        .thenReturn(new GachaPackProductQuote(9L, "시즌 1 가챠 카드팩", 120L, 1));
    when(walletService.deductForGachaPurchase(7L, 120L, 502L))
        .thenReturn(new PointDeductionResult(120L, 0L, 680L));
    when(userRepository.getReferenceById(7L)).thenReturn(user);
    when(drawRepository.saveAndFlush(any(GachaDraw.class)))
        .thenAnswer(
            invocation -> {
              GachaDraw draw = invocation.getArgument(0);
              ReflectionTestUtils.setField(draw, "id", 702L);
              return draw;
            });

    GachaPackPurchaseResponse response =
        service.purchase(7L, "legacy-key", new GachaPackPurchaseRequest(9L, 1, null));

    assertThat(response.unitPoint()).isEqualTo(120L);
    assertThat(response.totalPoint()).isEqualTo(120L);
    verify(walletService).deductForGachaPurchase(7L, 120L, 502L);
    verify(idempotencyService, never())
        .startWithCompatibleHash(any(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void legacyRequestReplaysLegacySuccessWithoutCheckingCurrentPrice() throws Exception {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            mock(UserRepository.class),
            mock(ApplicationEventPublisher.class),
            objectMapper);
    IdempotencyKey purchaseKey = mock(IdempotencyKey.class);
    GachaPackPurchaseResponse legacyResponse =
        new GachaPackPurchaseResponse(
            501L, 9L, "시즌 1 가챠 카드팩", 1, 100L, 100L, 60L, 40L, 800L, List.of(701L));

    when(purchaseKey.getResponseSnapshot())
        .thenReturn(objectMapper.writeValueAsString(legacyResponse));
    when(idempotencyService.start(7L, "GACHA_PACK_PURCHASE", "legacy-key", sha256("9:1")))
        .thenReturn(new IdempotencyExecution(purchaseKey, true));

    GachaPackPurchaseResponse response =
        service.purchase(7L, "legacy-key", new GachaPackPurchaseRequest(9L, 1, null));

    assertThat(response).isEqualTo(legacyResponse);
    verifyNoInteractions(productService, walletService, drawRepository);
  }

  @Test
  void legacyRetryReplaysPricedFormatSuccessWithSameProductAndQuantity() throws Exception {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            mock(UserRepository.class),
            mock(ApplicationEventPublisher.class),
            objectMapper);
    IdempotencyKey purchaseKey = mock(IdempotencyKey.class);
    GachaPackPurchaseResponse pricedResponse =
        new GachaPackPurchaseResponse(
            501L, 9L, "시즌 1 가챠 카드팩", 1, 100L, 100L, 60L, 40L, 800L, List.of(701L));

    when(idempotencyService.start(7L, "GACHA_PACK_PURCHASE", "purchase-key", sha256("9:1")))
        .thenThrow(new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_CONFLICT));
    when(idempotencyService.replaySucceededIgnoringHash(7L, "GACHA_PACK_PURCHASE", "purchase-key"))
        .thenReturn(new IdempotencyExecution(purchaseKey, true));
    when(purchaseKey.getResponseSnapshot())
        .thenReturn(objectMapper.writeValueAsString(pricedResponse));

    GachaPackPurchaseResponse response =
        service.purchase(7L, "purchase-key", new GachaPackPurchaseRequest(9L, 1, null));

    assertThat(response).isEqualTo(pricedResponse);
    verifyNoInteractions(productService, walletService, drawRepository);
  }

  @Test
  void legacyRetryDoesNotReplayPricedFormatSuccessForDifferentProduct() throws Exception {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            mock(UserRepository.class),
            mock(ApplicationEventPublisher.class),
            objectMapper);
    IdempotencyKey purchaseKey = mock(IdempotencyKey.class);
    GachaPackPurchaseResponse pricedResponse =
        new GachaPackPurchaseResponse(
            501L, 10L, "다른 카드팩", 1, 100L, 100L, 60L, 40L, 800L, List.of(701L));

    when(idempotencyService.start(7L, "GACHA_PACK_PURCHASE", "purchase-key", sha256("9:1")))
        .thenThrow(new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_CONFLICT));
    when(idempotencyService.replaySucceededIgnoringHash(7L, "GACHA_PACK_PURCHASE", "purchase-key"))
        .thenReturn(new IdempotencyExecution(purchaseKey, true));
    when(purchaseKey.getResponseSnapshot())
        .thenReturn(objectMapper.writeValueAsString(pricedResponse));

    assertThatThrownBy(
            () -> service.purchase(7L, "purchase-key", new GachaPackPurchaseRequest(9L, 1, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_IDEMPOTENCY_CONFLICT));
    verifyNoInteractions(productService, walletService, drawRepository);
  }

  @Test
  void legacyRequestKeepsExistingInProgressSemantics() {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            mock(UserRepository.class),
            mock(ApplicationEventPublisher.class),
            new ObjectMapper());

    when(idempotencyService.start(7L, "GACHA_PACK_PURCHASE", "legacy-key", sha256("9:1")))
        .thenThrow(new BusinessException(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS));

    assertThatThrownBy(
            () -> service.purchase(7L, "legacy-key", new GachaPackPurchaseRequest(9L, 1, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS));
    verifyNoInteractions(productService, walletService, drawRepository);
  }

  @Test
  void pricedRetryReplaysLegacySuccessWithoutCheckingCurrentPrice() throws Exception {
    ProductService productService = mock(ProductService.class);
    WalletService walletService = mock(WalletService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    GachaDrawRepository drawRepository = mock(GachaDrawRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    GachaPackPurchaseService service =
        new GachaPackPurchaseService(
            productService,
            walletService,
            idempotencyService,
            drawRepository,
            mock(UserRepository.class),
            mock(ApplicationEventPublisher.class),
            objectMapper);
    IdempotencyKey purchaseKey = mock(IdempotencyKey.class);
    GachaPackPurchaseResponse legacyResponse =
        new GachaPackPurchaseResponse(
            501L, 9L, "시즌 1 가챠 카드팩", 1, 100L, 100L, 60L, 40L, 800L, List.of(701L));

    when(purchaseKey.getResponseSnapshot())
        .thenReturn(objectMapper.writeValueAsString(legacyResponse));
    when(idempotencyService.startWithCompatibleHash(
            7L, "GACHA_PACK_PURCHASE", "legacy-key", sha256("9:1:999"), sha256("9:1")))
        .thenReturn(new IdempotencyExecution(purchaseKey, true));

    GachaPackPurchaseResponse response =
        service.purchase(7L, "legacy-key", new GachaPackPurchaseRequest(9L, 1, 999L));

    assertThat(response).isEqualTo(legacyResponse);
    verifyNoInteractions(productService, walletService, drawRepository);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
