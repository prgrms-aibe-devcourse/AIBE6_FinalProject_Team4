package com.kiwobollae.api.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.ai.chat.PlantChatConversationStore.ConversationHandle;
import com.kiwobollae.api.ai.chat.PlantChatConversationStore.ConversationRole;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlantChatConversationStoreTest {

  private MutableClock clock;
  private PlantChatConversationStore store;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-08-12T00:00:00Z"));
    store = new PlantChatConversationStore(clock);
  }

  @Test
  void storesOnlyServerCompletedConversationTurns() {
    UUID conversationId = completeNew(7L, 21L, "첫 질문", "서버가 생성한 첫 답변");

    try (ConversationHandle conversation = store.open(conversationId, 7L, 21L)) {
      assertThat(conversation.recentMessages())
          .extracting(message -> message.role() + ":" + message.content())
          .containsExactly("USER:첫 질문", "ASSISTANT:서버가 생성한 첫 답변");
    }
  }

  @Test
  void rejectsConversationOwnedByAnotherUserOrPlantWithSameError() {
    UUID conversationId = completeNew(7L, 21L, "첫 질문", "첫 답변");

    assertInvalidSession(() -> store.open(conversationId, 8L, 21L));
    assertInvalidSession(() -> store.open(conversationId, 7L, 22L));
  }

  @Test
  void expiresConversationAfterThirtyMinutes() {
    UUID conversationId = completeNew(7L, 21L, "첫 질문", "첫 답변");
    clock.advance(PlantChatConversationStore.SESSION_TTL);

    assertInvalidSession(() -> store.open(conversationId, 7L, 21L));
  }

  @Test
  void keepsAtMostThreeCompleteQuestionAnswerPairs() {
    UUID conversationId = completeNew(7L, 21L, "질문 1", "답변 1");
    for (int turn = 2; turn <= 4; turn++) {
      try (ConversationHandle conversation = store.open(conversationId, 7L, 21L)) {
        conversation.complete("질문 " + turn, "답변 " + turn);
      }
    }

    try (ConversationHandle conversation = store.open(conversationId, 7L, 21L)) {
      assertThat(conversation.recentMessages()).hasSize(6);
      assertThat(conversation.recentMessages().get(0).role()).isEqualTo(ConversationRole.USER);
      assertThat(conversation.recentMessages().get(0).content()).isEqualTo("질문 2");
      assertThat(conversation.recentMessages().get(5).role()).isEqualTo(ConversationRole.ASSISTANT);
      assertThat(conversation.recentMessages().get(5).content()).isEqualTo("답변 4");
    }
  }

  @Test
  void evictsOldestConversationWhenUserStartsMoreThanFive() {
    UUID oldest = completeNew(7L, 21L, "질문 1", "답변 1");
    for (int index = 2; index <= 6; index++) {
      clock.advance(Duration.ofSeconds(1));
      completeNew(7L, 21L, "질문 " + index, "답변 " + index);
    }

    assertInvalidSession(() -> store.open(oldest, 7L, 21L));
  }

  @Test
  void serializesConcurrentRequestsForSameConversation() throws Exception {
    UUID conversationId = completeNew(7L, 21L, "첫 질문", "첫 답변");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Integer> waitingRequest;
      try (ConversationHandle ignored = store.open(conversationId, 7L, 21L)) {
        waitingRequest =
            executor.submit(
                () -> {
                  try (ConversationHandle conversation = store.open(conversationId, 7L, 21L)) {
                    return conversation.recentMessages().size();
                  }
                });
        assertThatThrownBy(() -> waitingRequest.get(100, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
      }

      assertThat(waitingRequest.get(1, TimeUnit.SECONDS)).isEqualTo(2);
    } finally {
      executor.shutdownNow();
    }
  }

  private UUID completeNew(Long userId, Long plantProfileId, String question, String answer) {
    try (ConversationHandle conversation = store.open(null, userId, plantProfileId)) {
      return conversation.complete(question, answer);
    }
  }

  private void assertInvalidSession(ThrowingOperation operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode())
                  .isEqualTo(ErrorCode.AI_CHAT_CONVERSATION_INVALID);
              assertThat(exception.getMessage()).contains("AI 대화가 만료");
            });
  }

  @FunctionalInterface
  private interface ThrowingOperation {
    void run();
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
