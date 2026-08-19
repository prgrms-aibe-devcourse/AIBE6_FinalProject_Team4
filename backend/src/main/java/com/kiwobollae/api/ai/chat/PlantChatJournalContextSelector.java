package com.kiwobollae.api.ai.chat;

import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse.RecentJournal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 최근 기록은 항상 보존하고, 질문과 문자 단위로 겹치는 과거 기록만 제한해 따로 추가한다. */
@Component
final class PlantChatJournalContextSelector {

  static final int RECENT_JOURNAL_LIMIT = 5;
  static final int RELEVANT_HISTORY_LIMIT = 3;
  static final int RELEVANT_HISTORY_CHAR_BUDGET = 4_000;

  Selection select(List<RecentJournal> journals, String question) {
    List<RecentJournal> orderedJournals = journals == null ? List.of() : journals;
    List<RecentJournal> recentJournals =
        orderedJournals.stream().limit(RECENT_JOURNAL_LIMIT).toList();
    Set<String> questionNgrams = ngrams(question);
    Set<Integer> questionMeaningfulCharacters = meaningfulCharacters(question);
    if ((questionNgrams.isEmpty() && questionMeaningfulCharacters.isEmpty())
        || orderedJournals.size() <= RECENT_JOURNAL_LIMIT) {
      return new Selection(recentJournals, List.of());
    }

    List<ScoredJournal> scoredJournals =
        orderedJournals.stream()
            .skip(RECENT_JOURNAL_LIMIT)
            .map(
                journal ->
                    new ScoredJournal(
                        journal,
                        overlapScore(
                            questionNgrams, questionMeaningfulCharacters, journal.content())))
            .filter(scored -> scored.score() > 0)
            .sorted(
                Comparator.comparingInt(ScoredJournal::score)
                    .reversed()
                    .thenComparing(
                        scored -> scored.journal().writtenDate(), Comparator.reverseOrder())
                    .thenComparing(
                        scored -> scored.journal().journalId(), Comparator.reverseOrder()))
            .toList();

    List<RecentJournal> relatedPastJournals = new ArrayList<>();
    int consumed = 0;
    for (ScoredJournal scored : scoredJournals) {
      if (relatedPastJournals.size() >= RELEVANT_HISTORY_LIMIT) {
        break;
      }
      int contentLength = contentLength(scored.journal());
      if (consumed + contentLength > RELEVANT_HISTORY_CHAR_BUDGET) {
        continue;
      }
      relatedPastJournals.add(scored.journal());
      consumed += contentLength;
    }
    return new Selection(recentJournals, relatedPastJournals);
  }

  private int overlapScore(
      Set<String> questionNgrams, Set<Integer> questionMeaningfulCharacters, String content) {
    Set<String> contentNgrams = ngrams(content);
    int ngramScore = 0;
    for (String ngram : contentNgrams) {
      if (questionNgrams.contains(ngram)) {
        ngramScore++;
      }
    }
    int characterScore =
        meaningfulCharacters(content).stream()
            .mapToInt(character -> questionMeaningfulCharacters.contains(character) ? 1 : 0)
            .sum();
    if (ngramScore > 0) {
      return ngramScore * 10 + characterScore;
    }
    return characterScore >= 2 ? characterScore : 0;
  }

  private Set<String> ngrams(String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    StringBuilder normalized = new StringBuilder();
    value
        .codePoints()
        .filter(codePoint -> Character.isLetterOrDigit(codePoint))
        .forEach(normalized::appendCodePoint);
    if (normalized.length() < 2) {
      return Set.of();
    }

    Set<String> result = new HashSet<>();
    for (int index = 0; index < normalized.length() - 1; index++) {
      result.add(normalized.substring(index, index + 2));
    }
    return result;
  }

  private Set<Integer> meaningfulCharacters(String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    return value
        .codePoints()
        .filter(Character::isLetterOrDigit)
        .filter(character -> !isKoreanParticleOrEnding(character))
        .boxed()
        .collect(java.util.stream.Collectors.toSet());
  }

  private boolean isKoreanParticleOrEnding(int character) {
    return "은는이가을를에의와과도만로으론하였했어습니니다요때서고게".indexOf(character) >= 0;
  }

  private int contentLength(RecentJournal journal) {
    return journal.content() == null ? 0 : journal.content().length();
  }

  /** 최신순 기록과, 질문 관련성으로 따로 찾아낸 과거 기록을 구분해 담는다. */
  record Selection(List<RecentJournal> recentJournals, List<RecentJournal> relatedPastJournals) {
    Selection {
      recentJournals = List.copyOf(recentJournals);
      relatedPastJournals = List.copyOf(relatedPastJournals);
    }
  }

  private record ScoredJournal(RecentJournal journal, int score) {}
}
