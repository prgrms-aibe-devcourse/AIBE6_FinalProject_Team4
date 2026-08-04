package com.kiwobollae.api.ai.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.ErrorResponse;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class AiRateLimitExceptionHandlerTest {

  @Test
  void returnsRetryAfterHeaderFromRateLimitDetails() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/ai/plant-profiles/1/chat");
    BusinessException exception =
        new BusinessException(ErrorCode.COMMON_RATE_LIMITED, Map.of("retryAfterSeconds", 42L));

    ResponseEntity<ErrorResponse> response =
        new GlobalExceptionHandler().handleBusinessException(exception, request);

    assertThat(response.getStatusCode().value()).isEqualTo(429);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("COMMON_RATE_LIMITED");
  }
}
