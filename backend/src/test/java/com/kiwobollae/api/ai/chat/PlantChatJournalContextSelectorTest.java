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
  void ranksShortRelevantHistoryAboveLongUnrelatedHistory() {
    // bigram이 어긋나 글자 단위 폴백을 쓰는 상황. 겹친 글자 수만 세면 장문(겹침 6글자)이 단문(겹침 2글자)을 이긴다.
    String longUnrelatedContent =
        "화분 위치를 바꾸고 통풍을 늘렸습니다. 지지대를 세워 줄기를 노끈으로 묶고 흙 표면 상태를 적었습니다. "
            + "잎 개수와 줄기 굵기도 함께 확인하고 물받이 접시는 깨끗하게 닦았습니다. 물주기 알림을 켜 두었고 "
            + "창문 근처 온도가 조금 내려가서 밤에는 커튼을 쳐 두기로 했습니다.";
    List<RecentJournal> journals =
        List.of(
            journal(10L, "최근 기록"),
            journal(9L, "최근 기록"),
            journal(8L, "최근 기록"),
            journal(7L, "최근 기록"),
            journal(6L, "최근 기록"),
            journal(5L, "노란 잎 발견"),
            journal(4L, longUnrelatedContent));

    Selection selection = selector.select(journals, "잎끝이 노래졌던 때를 알려주세요");

    assertThat(selection.relatedPastJournals())
        .extracting(RecentJournal::journalId)
        .containsExactly(5L, 4L);
  }

  @Test
  void keepsSpeciesNameInitialCharacterThatAlsoLooksLikeAParticle() {
    // "고"를 조사로 보고 지우면 고추 관련 과거 기록이 폴백 점수에서 탈락한다.
    List<RecentJournal> journals =
        List.of(
            journal(10L, "최근 기록"),
            journal(9L, "최근 기록"),
            journal(8L, "최근 기록"),
            journal(7L, "최근 기록"),
            journal(6L, "최근 기록"),
            journal(5L, "고춧잎 색이 옅어요"),
            journal(4L, "물받이 접시를 닦았습니다."));

    Selection selection = selector.select(journals, "고추 잎이 왜 노래졌나요?");

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
