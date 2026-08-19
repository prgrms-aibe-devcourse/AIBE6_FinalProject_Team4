package com.kiwobollae.api.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.ai.chat.PlantChatJournalContextSelector.Selection;
import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse.RecentJournal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantChatJournalContextSelectorTest {

  private final PlantChatJournalContextSelector selector = new PlantChatJournalContextSelector();

  @Test
  void keepsFiveRecentJournalsAndSeparatesRelevantOlderJournal() {
    List<RecentJournal> journals =
        List.of(
            journal(10L, "오늘은 새 잎이 한 장 나왔어요."),
            journal(9L, "물을 주고 창가로 옮겼어요."),
            journal(8L, "줄기가 조금 자랐어요."),
            journal(7L, "흙 표면이 말라 물을 줬어요."),
            journal(6L, "잎 색은 대체로 초록색이에요."),
            journal(5L, "노란 잎이 생겨 물주기 간격을 조절했어요."),
            journal(4L, "화분을 닦고 주변을 정리했어요."));

    Selection selection = selector.select(journals, "예전에 노란 잎이 생겼을 때 어떻게 했나요?");

    assertThat(selection.recentJournals())
        .extracting(RecentJournal::journalId)
        .containsExactly(10L, 9L, 8L, 7L, 6L);
    assertThat(selection.relatedPastJournals())
        .extracting(RecentJournal::journalId)
        .containsExactly(5L);
  }

  @Test
  void returnsNoRelatedPastJournalsWhenHistoryFitsInRecentLimit() {
    List<RecentJournal> journals = List.of(journal(10L, "노란 잎이 생겼어요."), journal(9L, "물을 줬어요."));

    Selection selection = selector.select(journals, "노란 잎은 어떻게 해야 하나요?");

    assertThat(selection.recentJournals())
        .extracting(RecentJournal::journalId)
        .containsExactly(10L, 9L);
    assertThat(selection.relatedPastJournals()).isEmpty();
  }

  @Test
  void findsOlderJournalWhenTheQuestionUsesAChangedWordForm() {
    List<RecentJournal> journals =
        List.of(
            journal(10L, "오늘은 새 잎이 한 장 나왔어요."),
            journal(9L, "물을 주고 창가로 옮겼어요."),
            journal(8L, "줄기가 조금 자랐어요."),
            journal(7L, "흙 표면이 말라 물을 줬어요."),
            journal(6L, "잎 색은 대체로 초록색이에요."),
            journal(5L, "지난해 잎이 노랗게 변해 물주기 간격을 조절했어요."),
            journal(4L, "화분을 닦고 주변을 정리했어요."));

    Selection selection = selector.select(journals, "작년에 잎이 노래졌을 때 어떻게 했나요?");

    assertThat(selection.relatedPastJournals())
        .extracting(RecentJournal::journalId)
        .containsExactly(5L);
  }

  @Test
  void keepsRelevantHistoryWithinCharacterBudget() {
    String longContent = "가".repeat(PlantChatJournalContextSelector.RELEVANT_HISTORY_CHAR_BUDGET);
    List<RecentJournal> journals =
        List.of(
            journal(10L, "최근 기록"),
            journal(9L, "최근 기록"),
            journal(8L, "최근 기록"),
            journal(7L, "최근 기록"),
            journal(6L, "최근 기록"),
            journal(5L, longContent + "노란 잎"),
            journal(4L, "노란 잎이 생긴 과거 기록"));

    Selection selection = selector.select(journals, "노란 잎이 생긴 과거 기록을 알려주세요.");

    assertThat(selection.recentJournals())
        .extracting(RecentJournal::journalId)
        .containsExactly(10L, 9L, 8L, 7L, 6L);
    assertThat(selection.relatedPastJournals())
        .extracting(RecentJournal::journalId)
        .containsExactly(4L);
  }

  private RecentJournal journal(Long id, String content) {
    return new RecentJournal(id, LocalDate.of(2026, 8, 20).minusDays(10L - id), content);
  }
}
