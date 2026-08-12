package com.kiwobollae.api.ai.chat;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/** 단일 서버에서만 유지하는 식물 챗봇의 짧은 대화 문맥 저장소. 서버 재시작 시 대화는 사라진다. */
@Component
public class PlantChatConversationStore {

  static final Duration SESSION_TTL = Duration.ofMinutes(30);
  static final int MAX_SESSIONS_PER_USER = 5;
  static final int MAX_TOTAL_SESSIONS = 10_000;
  static final int MAX_CONTEXT_MESSAGES = 6;
  static final int MAX_CONTEXT_MESSAGE_LENGTH = 1000;
  static final int MAX_CONTEXT_TOTAL_LENGTH = 4000;

  private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();
  private final Clock seoulClock;

  public PlantChatConversationStore(Clock seoulClock) {
    this.seoulClock = seoulClock;
  }

  ConversationHandle open(UUID conversationId, Long userId, Long plantProfileId) {
    if (conversationId == null) {
      return ConversationHandle.forNew(this, userId, plantProfileId);
    }

    Conversation conversation = conversations.get(conversationId);
    if (conversation == null) {
      throw invalidSession();
    }

    conversation.lock.lock();
    Instant now = seoulClock.instant();
    if (conversations.get(conversationId) != conversation
        || conversation.isExpired(now)
        || !conversation.belongsTo(userId, plantProfileId)) {
      if (conversation.isExpired(now)) {
        conversations.remove(conversationId, conversation);
      }
      conversation.lock.unlock();
      throw invalidSession();
    }

    return ConversationHandle.forExisting(this, conversation);
  }

  private synchronized UUID create(
      Long userId, Long plantProfileId, String question, String assistantContent) {
    Instant now = seoulClock.instant();
    purgeExpired(now);
    enforceLimit(conversation -> conversation.userId.equals(userId), MAX_SESSIONS_PER_USER);
    enforceLimit(conversation -> true, MAX_TOTAL_SESSIONS);

    UUID conversationId;
    do {
      conversationId = UUID.randomUUID();
    } while (conversations.containsKey(conversationId));

    Conversation conversation = new Conversation(conversationId, userId, plantProfileId, now);
    conversation.append(question, assistantContent, now);
    conversations.put(conversationId, conversation);
    return conversationId;
  }

  private void purgeExpired(Instant now) {
    conversations.values().stream()
        .filter(conversation -> conversation.isExpired(now))
        .toList()
        .forEach(this::removeIfUnlocked);
  }

  private void enforceLimit(Predicate<Conversation> scope, int maxCount) {
    while (conversations.values().stream().filter(scope).count() >= maxCount) {
      Conversation oldest =
          conversations.values().stream()
              .filter(scope)
              .sorted(Comparator.comparing(conversation -> conversation.lastAccessAt))
              .filter(this::removeIfUnlocked)
              .findFirst()
              .orElse(null);
      if (oldest == null) {
        // 모두 사용 중이면 이번 생성은 허용한다. 외부 AI 호출이 성공한 뒤 저장 상한 때문에 응답을
        // 실패시키지 않으며, 잠금이 풀린 뒤 다음 생성 시 다시 상한을 정리한다.
        return;
      }
    }
  }

  private boolean removeIfUnlocked(Conversation conversation) {
    if (!conversation.lock.tryLock()) {
      return false;
    }
    try {
      return conversations.remove(conversation.id, conversation);
    } finally {
      conversation.lock.unlock();
    }
  }

  private BusinessException invalidSession() {
    return new BusinessException(ErrorCode.AI_CHAT_CONVERSATION_INVALID);
  }

  enum ConversationRole {
    USER,
    ASSISTANT
  }

  record ConversationMessage(ConversationRole role, String content) {}

  static final class ConversationHandle implements AutoCloseable {

    private final PlantChatConversationStore store;
    private final Long userId;
    private final Long plantProfileId;
    private final Conversation conversation;
    private boolean completed;
    private boolean closed;

    private ConversationHandle(
        PlantChatConversationStore store,
        Long userId,
        Long plantProfileId,
        Conversation conversation) {
      this.store = store;
      this.userId = userId;
      this.plantProfileId = plantProfileId;
      this.conversation = conversation;
    }

    private static ConversationHandle forNew(
        PlantChatConversationStore store, Long userId, Long plantProfileId) {
      return new ConversationHandle(store, userId, plantProfileId, null);
    }

    private static ConversationHandle forExisting(
        PlantChatConversationStore store, Conversation conversation) {
      return new ConversationHandle(
          store, conversation.userId, conversation.plantProfileId, conversation);
    }

    List<ConversationMessage> recentMessages() {
      ensureOpen();
      return conversation == null ? List.of() : List.copyOf(conversation.messages);
    }

    UUID complete(String question, String assistantContent) {
      ensureOpen();
      if (completed) {
        throw new IllegalStateException("대화 요청은 한 번만 완료할 수 있습니다.");
      }
      completed = true;

      if (conversation == null) {
        return store.create(userId, plantProfileId, question, assistantContent);
      }
      conversation.append(question, assistantContent, store.seoulClock.instant());
      return conversation.id;
    }

    private void ensureOpen() {
      if (closed) {
        throw new IllegalStateException("이미 종료된 대화 핸들입니다.");
      }
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      if (conversation != null) {
        conversation.lock.unlock();
      }
    }
  }

  private static final class Conversation {

    private final UUID id;
    private final Long userId;
    private final Long plantProfileId;
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<ConversationMessage> messages = new ArrayDeque<>();
    private volatile Instant lastAccessAt;

    private Conversation(UUID id, Long userId, Long plantProfileId, Instant lastAccessAt) {
      this.id = id;
      this.userId = userId;
      this.plantProfileId = plantProfileId;
      this.lastAccessAt = lastAccessAt;
    }

    private boolean belongsTo(Long requestedUserId, Long requestedPlantProfileId) {
      return userId.equals(requestedUserId) && plantProfileId.equals(requestedPlantProfileId);
    }

    private boolean isExpired(Instant now) {
      return !lastAccessAt.plus(SESSION_TTL).isAfter(now);
    }

    private void append(String question, String assistantContent, Instant now) {
      messages.addLast(new ConversationMessage(ConversationRole.USER, normalizeContext(question)));
      messages.addLast(
          new ConversationMessage(ConversationRole.ASSISTANT, normalizeContext(assistantContent)));
      trimContext();
      lastAccessAt = now;
    }

    private void trimContext() {
      while (messages.size() > MAX_CONTEXT_MESSAGES || totalLength() > MAX_CONTEXT_TOTAL_LENGTH) {
        messages.removeFirst();
        messages.removeFirst();
      }
    }

    private int totalLength() {
      return messages.stream().mapToInt(message -> message.content().length()).sum();
    }

    private String normalizeContext(String content) {
      String normalized = content == null ? "" : content.strip();
      return normalized.substring(0, Math.min(normalized.length(), MAX_CONTEXT_MESSAGE_LENGTH));
    }
  }
}
