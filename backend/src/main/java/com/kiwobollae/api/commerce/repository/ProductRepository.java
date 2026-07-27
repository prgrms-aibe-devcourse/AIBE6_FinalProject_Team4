package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@EntityGraph(attributePaths = "plant")
	Page<Product> findAllByStatusAndCategoryIn(
			ProductStatus status,
			Collection<ProductCategory> categories,
			Pageable pageable
	);

	@EntityGraph(attributePaths = "plant")
	Optional<Product> findByIdAndStatus(Long id, ProductStatus status);
}
