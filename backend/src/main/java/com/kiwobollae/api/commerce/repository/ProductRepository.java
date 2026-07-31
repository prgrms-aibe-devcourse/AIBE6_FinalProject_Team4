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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsByCategory(ProductCategory category);

	@EntityGraph(attributePaths = "plant")
	Page<Product> findAllByStatusAndCategoryIn(
			ProductStatus status,
			Collection<ProductCategory> categories,
			Pageable pageable
	);

	@EntityGraph(attributePaths = "plant")
	Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

	@Modifying
	@Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
	int decrementStockIfAvailable(@Param("id") Long id, @Param("qty") Integer qty);

	@Modifying
	@Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
	int incrementStock(@Param("id") Long id, @Param("qty") Integer qty);
}
