package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.commerce.dto.response.ProductDetailResponse;
import com.kiwobollae.api.commerce.dto.response.ProductPageResponse;
import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.asset.AssetUrlResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private AssetUrlResolver assetUrlResolver;

	@InjectMocks
	private ProductService productService;

	@Test
	void getProductsReturnsOnlyActiveShopCategoriesWithLatestSort() {
		Product product = product(1L, ProductCategory.KIT, 0, null);
		given(productRepository.findAllByStatusAndCategoryIn(
				eq(ProductStatus.ACTIVE),
				anyCollection(),
				any(Pageable.class)
		)).willReturn(new PageImpl<>(List.of(product)));

		ProductPageResponse response = productService.getProducts(null, "LATEST", 0, 20);

		assertThat(response.content()).hasSize(1);
		assertThat(response.content().getFirst().soldOut()).isTrue();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ProductCategory>> categoriesCaptor = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(productRepository).findAllByStatusAndCategoryIn(
				eq(ProductStatus.ACTIVE),
				categoriesCaptor.capture(),
				pageableCaptor.capture()
		);
		assertThat(categoriesCaptor.getValue()).containsExactly(
				ProductCategory.KIT,
				ProductCategory.SEEDLING,
				ProductCategory.GACHA_PACK
		);
		assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
		assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection())
				.isEqualTo(Sort.Direction.DESC);
	}

	@Test
	void getProductsAppliesCategoryAndPriceSort() {
		given(productRepository.findAllByStatusAndCategoryIn(
				eq(ProductStatus.ACTIVE),
				anyCollection(),
				any(Pageable.class)
		)).willReturn(new PageImpl<>(List.of()));

		productService.getProducts("seedling", "PRICE_ASC", 2, 10);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ProductCategory>> categoriesCaptor = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(productRepository).findAllByStatusAndCategoryIn(
				eq(ProductStatus.ACTIVE),
				categoriesCaptor.capture(),
				pageableCaptor.capture()
		);
		assertThat(categoriesCaptor.getValue()).containsExactly(ProductCategory.SEEDLING);
		assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
		assertThat(pageableCaptor.getValue().getSort().getOrderFor("pointPrice").getDirection())
				.isEqualTo(Sort.Direction.ASC);
	}

	@Test
	void getProductsAcceptsGachaPackCategory() {
		given(productRepository.findAllByStatusAndCategoryIn(
				eq(ProductStatus.ACTIVE),
				anyCollection(),
				any(Pageable.class)
		)).willReturn(new PageImpl<>(List.of()));

		productService.getProducts("gacha_pack", "LATEST", 0, 20);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ProductCategory>> categoriesCaptor = ArgumentCaptor.forClass(List.class);
		verify(productRepository).findAllByStatusAndCategoryIn(
				eq(ProductStatus.ACTIVE),
				categoriesCaptor.capture(),
				any(Pageable.class)
		);
		assertThat(categoriesCaptor.getValue()).containsExactly(ProductCategory.GACHA_PACK);
	}

	@Test
	void getActiveGachaPackReturnsServerPriceAndMaximumQuantity() {
		Product product = org.mockito.Mockito.mock(Product.class);
		given(product.getId()).willReturn(9L);
		given(product.getName()).willReturn("시즌 1 가챠 카드팩");
		given(product.getCategory()).willReturn(ProductCategory.GACHA_PACK);
		given(product.getPointPrice()).willReturn(100L);
		given(productRepository.findByIdAndStatus(9L, ProductStatus.ACTIVE))
				.willReturn(Optional.of(product));

		var quote = productService.getActiveGachaPack(9L);

		assertThat(quote.productId()).isEqualTo(9L);
		assertThat(quote.unitPoint()).isEqualTo(100L);
		assertThat(quote.maxQuantity()).isEqualTo(1);
	}

	@Test
	void getProductTreatsGachaPackAsUnlimitedRegardlessOfStock() {
		Product product = product(9L, ProductCategory.GACHA_PACK, 0, null);
		given(productRepository.findByIdAndStatus(9L, ProductStatus.ACTIVE))
				.willReturn(Optional.of(product));

		var response = productService.getProduct(9L);

		assertThat(response.soldOut()).isFalse();
	}

	@Test
	void getProductsRejectsExchangeCategory() {
		assertThatThrownBy(() -> productService.getProducts("EXCHANGE", "LATEST", 0, 20))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

		verify(productRepository, never()).findAllByStatusAndCategoryIn(
				any(),
				anyCollection(),
				any(Pageable.class)
		);
	}

	@Test
	void getProductsRejectsInvalidPageSize() {
		assertThatThrownBy(() -> productService.getProducts(null, "LATEST", 0, 101))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
	}

	@Test
	void getProductIncludesPlantGuideForSeedling() {
		PlantSpecies plantSpecies = org.mockito.Mockito.mock(PlantSpecies.class);
		given(plantSpecies.getId()).willReturn(11L);
		given(plantSpecies.getName()).willReturn("방울토마토");
		given(plantSpecies.getCategory()).willReturn("VEGETABLE");
		given(plantSpecies.getCareGuide()).willReturn("햇빛이 잘 드는 곳에서 키워주세요.");
		Product product = product(3L, ProductCategory.SEEDLING, 5, plantSpecies);
		given(productRepository.findByIdAndStatus(3L, ProductStatus.ACTIVE))
				.willReturn(Optional.of(product));

		ProductDetailResponse response = productService.getProduct(3L);

		assertThat(response.plantGuide()).isNotNull();
		assertThat(response.plantGuide().plantSpeciesId()).isEqualTo(11L);
		assertThat(response.plantGuide().careGuide()).isEqualTo("햇빛이 잘 드는 곳에서 키워주세요.");
	}

	@Test
	void getProductTreatsHiddenOrMissingProductAsNotFound() {
		given(productRepository.findByIdAndStatus(99L, ProductStatus.ACTIVE))
				.willReturn(Optional.empty());

		assertThatThrownBy(() -> productService.getProduct(99L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
	}

	private Product product(Long id, ProductCategory category, int stock, PlantSpecies plantSpecies) {
		Product product = org.mockito.Mockito.mock(Product.class);
		given(product.getId()).willReturn(id);
		given(product.getName()).willReturn("테스트 상품");
		given(product.getCategory()).willReturn(category);
		given(product.getPointPrice()).willReturn(1000L);
		given(product.getStock()).willReturn(stock);
		given(product.getImageUrl()).willReturn("https://example.com/product.png");
		if (plantSpecies != null) {
			given(product.getPlant()).willReturn(plantSpecies);
		}
		return product;
	}
}
