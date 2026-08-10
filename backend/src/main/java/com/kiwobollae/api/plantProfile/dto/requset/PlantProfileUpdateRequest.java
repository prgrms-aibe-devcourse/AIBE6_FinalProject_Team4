package com.kiwobollae.api.plantProfile.dto.requset;

import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlantProfileUpdateRequest(
		@Pattern(regexp = ".*\\S.*", message = "별명은 공백만으로 이루어질 수 없습니다.")
		@Size(max = 50) String nickname,
		@Size(max = 500) String thumbnailUrl,
		PlantStatus status
) {
}
