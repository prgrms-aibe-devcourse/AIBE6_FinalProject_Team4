package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kiwobollae.api.commerce.dto.request.AdminProductRequest;
import com.kiwobollae.api.commerce.dto.response.AdminProductResponse;
import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.asset.AssetUrlResolver;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminProductServiceTest {

  private ProductRepository productRepository;
  private AdminProductService service;

  @BeforeEach
  void setUp() {
    productRepository = mock(ProductRepository.class);
    service =
        new AdminProductService(
            productRepository,
            mock(PlantSpeciesRepository.class),
            new CommerceAssetKeyValidator(),
            new AssetUrlResolver("https://assets.example.com/"));
  }

  @Test
  void createsGachaPackWithUnlimitedStockRepresentation() {
    given(productRepository.saveAndFlush(any(Product.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    AdminProductResponse response =
        service.create(
            new AdminProductRequest(
                "시즌 팩", ProductCategory.GACHA_PACK, 100L, 999, null, "카드 5장", null));

    assertThat(response.stock()).isZero();
    assertThat(response.unlimitedStock()).isTrue();
    assertThat(response.soldOut()).isFalse();
  }

  @Test
  void editingProductDoesNotOverwriteStockWithAbsoluteValue() {
    Product product =
        Product.builder()
            .name("키트")
            .category(ProductCategory.KIT)
            .pointPrice(100L)
            .stock(7)
            .status(ProductStatus.ACTIVE)
            .build();
    given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));

    AdminProductResponse response =
        service.update(
            1L,
            new AdminProductRequest(
                "수정 키트", ProductCategory.KIT, 200L, 999, null, null, null));

    assertThat(response.name()).isEqualTo("수정 키트");
    assertThat(response.pointPrice()).isEqualTo(200L);
    assertThat(response.stock()).isEqualTo(7);
  }

  @Test
  void rejectsStockAdjustmentForUnlimitedGachaPack() {
    Product product =
        Product.builder()
            .name("시즌 팩")
            .category(ProductCategory.GACHA_PACK)
            .pointPrice(100L)
            .stock(0)
            .status(ProductStatus.ACTIVE)
            .build();
    given(productRepository.adjustStock(1L, 1, ProductCategory.GACHA_PACK)).willReturn(0);
    given(productRepository.findById(1L)).willReturn(Optional.of(product));

    assertThatThrownBy(() -> service.adjustStock(1L, 1))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("요청 값이 올바르지 않습니다");
  }
}
