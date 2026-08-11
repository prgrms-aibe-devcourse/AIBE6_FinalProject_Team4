package com.kiwobollae.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.species.repository.PlantSpeciesRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductInitDataTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private PlantSpeciesRepository plantSpeciesRepository;

	@InjectMocks
	private ProductInitData productInitData;

	@Test
	void seedsTenActiveProductsWhenProductsAreEmpty() {
		given(productRepository.count()).willReturn(0L);
		given(plantSpeciesRepository.findAll()).willReturn(List.of());
		given(plantSpeciesRepository.save(org.mockito.ArgumentMatchers.any(PlantSpecies.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		productInitData.run(null);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Product>> productsCaptor = ArgumentCaptor.forClass(List.class);
		verify(productRepository).saveAll(productsCaptor.capture());

		List<Product> products = productsCaptor.getValue();
		assertThat(products).hasSize(10);
		assertThat(products).allMatch(product -> product.getStatus() == ProductStatus.ACTIVE);
		assertThat(products).allMatch(product -> product.getPointPrice() != null && product.getPointPrice() >= 0);
		assertThat(products).allMatch(product -> product.getStock() >= 0);
		assertThat(products).filteredOn(product -> product.getCategory() == ProductCategory.KIT).hasSize(5);
		assertThat(products)
				.filteredOn(product -> product.getCategory() == ProductCategory.SEEDLING)
				.hasSize(5)
				.allMatch(product -> product.getPlant() != null);
	}

	@Test
	void skipsSeedingWhenAnyProductAlreadyExists() {
		given(productRepository.count()).willReturn(1L);

		productInitData.run(null);

		verify(plantSpeciesRepository, never()).findAll();
		verify(productRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
	}
}
