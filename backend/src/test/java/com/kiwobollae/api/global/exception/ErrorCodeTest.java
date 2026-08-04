package com.kiwobollae.api.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

	@Test
	void timelapseErrorCodesHaveExpectedHttpStatus() {
		assertThat(ErrorCode.TIMELAPSE_NOT_HARVESTED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ErrorCode.TIMELAPSE_INSUFFICIENT_IMAGES.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ErrorCode.TIMELAPSE_ALREADY_PROCESSING.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
	}
}
