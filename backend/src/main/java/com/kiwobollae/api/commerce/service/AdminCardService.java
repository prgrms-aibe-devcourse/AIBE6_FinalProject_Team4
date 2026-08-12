package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.dto.request.CardRequest;
import com.kiwobollae.api.commerce.dto.response.AdminCardResponse;
import com.kiwobollae.api.commerce.dto.response.AdminExchangeProductOptionResponse;
import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.ExchangeProductRepository;
import com.kiwobollae.api.global.asset.AssetUrlResolver;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCardService {

  private final CardRepository cardRepository;
  private final ExchangeProductRepository exchangeProductRepository;
  private final CommerceAssetKeyValidator assetKeyValidator;
  private final AssetUrlResolver assetUrlResolver;
  private final CommerceAssetStorageService assetStorageService;

  public List<AdminCardResponse> getCards() {
    return cardRepository.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
  }

  public List<AdminExchangeProductOptionResponse> getActiveExchangeProducts() {
    return exchangeProductRepository
        .findAllByStatusOrderByCreatedAtDesc(ActiveStatus.ON_SALE)
        .stream()
        .map(AdminExchangeProductOptionResponse::from)
        .toList();
  }

  @Transactional
  public AdminCardResponse create(CardRequest request) {
    ExchangeProduct exchangeProduct = requireActiveExchangeProduct(request.exchangeProductId());
    Card card =
        cardRepository.saveAndFlush(
            Card.builder()
                .name(request.name().trim())
                .pointPrice(request.pointPrice())
                .exchangeProduct(exchangeProduct)
                .requiredCountForExchange(request.requiredCountForExchange())
                .description(trimToNull(request.description()))
                .status(request.status())
                .build());
    String imageKey = assetKeyValidator.validate(request.imageUrl(), "coupons", card.getId());
    card.updateInfo(
        card.getName(),
        card.getPointPrice(),
        card.getExchangeProduct(),
        card.getRequiredCountForExchange(),
        card.getDescription(),
        imageKey);
    return response(card);
  }

  @Transactional
  public AdminCardResponse update(Long cardId, CardRequest request) {
    Card card = findForUpdate(cardId);
    ExchangeProduct exchangeProduct = requireActiveExchangeProduct(request.exchangeProductId());
    card.updateInfo(
        request.name().trim(),
        request.pointPrice(),
        exchangeProduct,
        request.requiredCountForExchange(),
        trimToNull(request.description()),
        assetKeyValidator.validate(request.imageUrl(), "coupons", cardId));
    card.changeStatus(request.status());
    return response(card);
  }

  @Transactional
  public AdminCardResponse changeStatus(Long cardId, ActiveStatus status) {
    Card card = findForUpdate(cardId);
    card.changeStatus(status);
    return response(card);
  }

  @Transactional
  public AdminCardResponse hide(Long cardId) {
    return changeStatus(cardId, ActiveStatus.HIDDEN);
  }

  @Transactional
  public AdminCardResponse uploadImage(Long cardId, MultipartFile file) {
    Card card = findForUpdate(cardId);
    card.updateImage(assetStorageService.upload(file, "coupons", cardId));
    return response(card);
  }

  private Card findForUpdate(Long cardId) {
    return cardRepository
        .findByIdForUpdate(cardId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
  }

  private ExchangeProduct requireActiveExchangeProduct(Long exchangeProductId) {
    return exchangeProductRepository
        .findByIdAndStatus(exchangeProductId, ActiveStatus.ON_SALE)
        .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_PRODUCT_NOT_FOUND));
  }

  private AdminCardResponse response(Card card) {
    return AdminCardResponse.from(card, assetUrlResolver.resolve(card.getImageUrl()));
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
