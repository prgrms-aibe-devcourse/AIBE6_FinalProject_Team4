package com.kiwobollae.api.plantProfile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PlantProfileRequest(
		@NotNull Long speciesId,
		@NotBlank @Size(max = 50)
		@Pattern(regexp = "[가-힣a-zA-Z0-9 ]*", message = "별명에는 한글/영문/숫자/공백만 사용할 수 있습니다.")
		String nickname,
		@NotNull LocalDate startDate,
		@Size(max = 500) String thumbnailUrl
) {
}
