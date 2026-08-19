package com.kiwobollae.api.ai.chat;

import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse.RecentJournal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 최근 기록은 항상 보존하고, 질문과 겹치는 과거 기록만 따로 골라 제한해 추가한다. 겹침은 개수가 아니라 질문·본문 합집합 대비 비율로 재기 때문에 본문이 길다는 이유만으로
 * 점수가 오르지 않는다.
 */
@Component
final class PlantChatJournalContextSelector {

  static final int RECENT_JOURNAL_LIMIT = 5;
  static final int RELEVANT_HISTORY_LIMIT = 3;
  static final int RELEVANT_HISTORY_CHAR_BUDGET = 4_000;

  /** 겹침 비율을 정수로 비교하기 위한 배율. 비율 점수는 0 이상 SIMILARITY_SCALE 이하가 된다. */
  private static final int SIMILARITY_SCALE = 10_000;

  /** bigram이 하나라도 겹치면 글자 단위 폴백 점수보다 항상 앞서도록 더하는 가중치. */
  private static final int NGRAM_MATCH_TIER = SIMILARITY_SCALE + 1;

  /** 폴백에서 우연한 한 글자 일치를 걸러내기 위한 최소 겹침 글자 수. */
  private static final int MIN_FALLBACK_CHARACTER_OVERLAP = 2;

  /**
   * 조사·어미 후보 글자. 토큰의 첫 글자일 때는 제거하지 않는다. 고추·가지·도라지·이끼·로즈마리·하월시아·게발선인장처럼 종 이름의 첫 글자가 조사와 겹치므로, 단어 끝에
   * 붙은 경우만 조사·어미로 본다.
   */
  private static final String PARTICLE_OR_ENDING_CANDIDATES = "은는이가을를에의와과도만로으론하였했어습니다요때서고게";

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
    int ngramOverlap = overlapCount(questionNgrams, contentNgrams);
    if (ngramOverlap > 0) {
      return NGRAM_MATCH_TIER
          + similarityScore(ngramOverlap, questionNgrams.size(), contentNgrams.size());
    }

    // 활용 변화로 bigram이 어긋난 경우에만 글자 단위로 비교한다. 겹친 글자 수를 그대로 쓰면 무관한 장문이
    // 관련 있는 단문을 이기므로 길이를 정규화한 비율로만 순위를 정한다.
    Set<Integer> contentMeaningfulCharacters = meaningfulCharacters(content);
    int characterOverlap = overlapCount(questionMeaningfulCharacters, contentMeaningfulCharacters);
    if (characterOverlap < MIN_FALLBACK_CHARACTER_OVERLAP) {
      return 0;
    }
    return similarityScore(
        characterOverlap, questionMeaningfulCharacters.size(), contentMeaningfulCharacters.size());
  }

  private <T> int overlapCount(Set<T> questionTokens, Set<T> contentTokens) {
    int overlap = 0;
    for (T token : contentTokens) {
      if (questionTokens.contains(token)) {
        overlap++;
      }
    }
    return overlap;
  }

  /** 자카드 유사도(겹침 / 합집합)를 정수 배율로 환산한다. */
  private int similarityScore(int overlap, int questionSize, int contentSize) {
    int union = questionSize + contentSize - overlap;
    return union <= 0 ? 0 : overlap * SIMILARITY_SCALE / union;
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

    Set<Integer> characters = new HashSet<>();
    boolean atTokenStart = true;
    int index = 0;
    while (index < value.length()) {
      int character = value.codePointAt(index);
      index += Character.charCount(character);
      if (!Character.isLetterOrDigit(character)) {
        atTokenStart = true;
        continue;
      }
      if (atTokenStart || !isParticleOrEndingCandidate(character)) {
        characters.add(character);
      }
      atTokenStart = false;
    }
    return characters;
  }

  private boolean isParticleOrEndingCandidate(int character) {
    return PARTICLE_OR_ENDING_CANDIDATES.indexOf(character) >= 0;
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
