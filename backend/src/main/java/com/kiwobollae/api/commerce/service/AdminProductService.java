package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.dto.request.AdminProductRequest;
import com.kiwobollae.api.commerce.dto.response.AdminProductResponse;
import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.asset.AssetUrlResolver;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

  private static final List<ProductCategory> MANAGED_CATEGORIES =
      List.of(ProductCategory.KIT, ProductCategory.SEEDLING, ProductCategory.GACHA_PACK);

  private final ProductRepository productRepository;
  private final PlantSpeciesRepository plantSpeciesRepository;
  private final CommerceAssetKeyValidator assetKeyValidator;
  private final AssetUrlResolver assetUrlResolver;

  public List<AdminProductResponse> getProducts() {
    return productRepository.findAllByCategoryInOrderByCreatedAtDesc(MANAGED_CATEGORIES).stream()
        .map(this::response)
        .toList();
  }

  @Transactional
  public AdminProductResponse create(AdminProductRequest request) {
    ValidatedProduct values = validate(request);
    Product product =
        productRepository.saveAndFlush(
            Product.builder()
                .name(values.name())
                .category(values.category())
                .pointPrice(values.pointPrice())
                .stock(values.stock())
                .plant(values.plant())
                .description(values.description())
                .status(ProductStatus.ACTIVE)
                .build());
    String imageKey = assetKeyValidator.validate(values.imageUrl(), "products", product.getId());
    product.updateInfo(
        product.getName(),
        product.getCategory(),
        product.getPointPrice(),
        product.getStock(),
        product.getPlant(),
        product.getDescription(),
        imageKey);
    return response(product);
  }

  @Transactional
  public AdminProductResponse update(Long productId, AdminProductRequest request) {
    Product product = findManagedForUpdate(productId);
    ValidatedProduct values = validate(request);
    if (product.getCategory() != values.category()) {
      throw invalid("category", values.category(), "등록 후 상품 카테고리는 변경할 수 없습니다.");
    }
    product.updateInfo(
        values.name(),
        values.category(),
        values.pointPrice(),
        product.getStock(),
        values.plant(),
        values.description(),
        assetKeyValidator.validate(values.imageUrl(), "products", productId));
    return response(product);
  }

  @Transactional
  public AdminProductResponse adjustStock(Long productId, int delta) {
    if (delta == 0) {
      throw invalid("delta", delta, "0이 아닌 증감량이어야 합니다.");
    }
    int updated =
        productRepository.adjustStock(productId, delta, ProductCategory.GACHA_PACK);
    if (updated == 0) {
      Product product = findManaged(productId);
      if (product.getCategory() == ProductCategory.GACHA_PACK) {
        throw invalid("category", product.getCategory(), "가챠 팩 재고는 무제한입니다.");
      }
      throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
    }
    return response(findManaged(productId));
  }

  @Transactional
  public AdminProductResponse changeStatus(Long productId, ProductStatus status) {
    Product product = findManaged(productId);
    productRepository.updateStatusIfChanged(productId, status);
    return response(findManaged(productId));
  }

  @Transactional
  public AdminProductResponse hide(Long productId) {
    return changeStatus(productId, ProductStatus.HIDDEN);
  }

  private ValidatedProduct validate(AdminProductRequest request) {
    if (request == null || !MANAGED_CATEGORIES.contains(request.category())) {
      throw invalid(
          "category",
          request == null ? null : request.category(),
          "KIT, SEEDLING 또는 GACHA_PACK만 관리할 수 있습니다.");
    }
    if (request.category() == ProductCategory.GACHA_PACK && request.pointPrice() < 1) {
      throw invalid("pointPrice", request.pointPrice(), "가챠 팩 가격은 1P 이상이어야 합니다.");
    }
    PlantSpecies plant = null;
    if (request.category() == ProductCategory.SEEDLING && request.plantId() != null) {
      plant =
          plantSpeciesRepository
              .findById(request.plantId())
              .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
    int stock = request.category() == ProductCategory.GACHA_PACK ? 0 : request.stock();
    return new ValidatedProduct(
        request.name().trim(),
        request.category(),
        request.pointPrice(),
        stock,
        plant,
        trimToNull(request.description()),
        trimToNull(request.imageUrl()));
  }

  private Product findManagedForUpdate(Long productId) {
    Product product =
        productRepository
            .findByIdForUpdate(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    requireManaged(product);
    return product;
  }

  private Product findManaged(Long productId) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    requireManaged(product);
    return product;
  }

  private void requireManaged(Product product) {
    if (!MANAGED_CATEGORIES.contains(product.getCategory())) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
    }
  }

  private BusinessException invalid(String field, Object value, String reason) {
    return new BusinessException(
        ErrorCode.COMMON_VALIDATION_FAILED,
        Map.of("field", field, "rejectedValue", String.valueOf(value), "reason", reason));
  }

  private AdminProductResponse response(Product product) {
    return AdminProductResponse.from(product, assetUrlResolver.resolve(product.getImageUrl()));
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private record ValidatedProduct(
      String name,
      ProductCategory category,
      Long pointPrice,
      Integer stock,
      PlantSpecies plant,
      String description,
      String imageUrl) {}
}
