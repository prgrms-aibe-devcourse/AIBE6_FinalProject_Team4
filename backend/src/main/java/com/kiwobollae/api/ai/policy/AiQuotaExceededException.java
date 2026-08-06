package com.kiwobollae.api.ai.policy;

/** 내부 카운터가 호출을 예약하지 못했음을 가드에 알린다. */
final class AiQuotaExceededException extends RuntimeException {

  private final long retryAfterSeconds;

  AiQuotaExceededException(long retryAfterSeconds) {
    this.retryAfterSeconds = retryAfterSeconds;
  }

  long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
