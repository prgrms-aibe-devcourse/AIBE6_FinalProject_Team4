package com.kiwobollae.api.species.controller;

import com.kiwobollae.api.species.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.species.service.PlantSpeciesService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "식물 종", description = "식물 종 조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/plants/species")
public class PlantSpeciesController {

	private final PlantSpeciesService plantSpeciesService;

	@Operation(summary = "식물 종 목록 조회", description = "등록된 식물 종 전체 목록을 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<List<PlantSpeciesResponse>>> getAllSpecies() {
		return ResponseEntity.ok(ApiResponse.success(plantSpeciesService.getAllSpecies()));
	}

	@Operation(summary = "식물 종 단건 조회", description = "식물 종 하나를 id로 조회합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<PlantSpeciesResponse>> getSpecies(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(plantSpeciesService.getSpecies(id)));
	}
}
