package com.kiwobollae.api.plantProfile.dto.requset;

import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlantProfileUpdateRequest(
		@Pattern(regexp = ".*\\S.*", message = "별명은 공백만으로 이루어질 수 없습니다.")
		@Pattern(regexp = "[가-힣a-zA-Z0-9 ]*", message = "별명에는 한글/영문/숫자/공백만 사용할 수 있습니다.")
		@Size(max = 50) String nickname,
		@Size(max = 500) String thumbnailUrl,
		PlantStatus status
) {
}
