package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.PlantProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantProfileRepository extends JpaRepository<PlantProfile, Long> {

	@Query("select p from PlantProfile p join fetch p.species "
			+ "where p.user.id = :userId order by p.createdAt desc")
	List<PlantProfile> findAllByUserId(@Param("userId") Long userId);

	@Query("select p from PlantProfile p join fetch p.species "
			+ "where p.id = :id and p.user.id = :userId")
	Optional<PlantProfile> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
