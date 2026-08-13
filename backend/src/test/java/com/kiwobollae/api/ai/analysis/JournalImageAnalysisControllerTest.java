package com.kiwobollae.api.ai.analysis;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.ai.analysis.dto.JournalImageAnalysisResponse;
import com.kiwobollae.api.ai.analysis.dto.JournalImageAnalysisResult.ImageQuality;
import com.kiwobollae.api.ai.analysis.dto.JournalImageAnalysisResult.PlantCondition;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class JournalImageAnalysisControllerTest {

  private static final String IMAGE_HASH = "a".repeat(64);

  @Mock private JournalImageAnalysisService analysisService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new JournalImageAnalysisController(analysisService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void analyzesSavedJournalImage() throws Exception {
    given(analysisService.analyze(7L, 31L, IMAGE_HASH)).willReturn(response());

    mockMvc
        .perform(
            post("/api/v1/ai/journals/31/image-analysis")
                .contentType(APPLICATION_JSON)
                .content("{\"imageHash\":\"%s\"}".formatted(IMAGE_HASH)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.journalId").value(31))
        .andExpect(jsonPath("$.data.imageHash").value(IMAGE_HASH))
        .andExpect(jsonPath("$.data.condition").value("NEEDS_ATTENTION"));
  }

  @Test
  void rejectsBlankImageHashAtHttpBoundary() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ai/journals/31/image-analysis")
                .contentType(APPLICATION_JSON)
                .content("{\"imageHash\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

    verifyNoInteractions(analysisService);
  }

  @Test
  void returnsCompletedResultsForOwnedJournal() throws Exception {
    given(analysisService.getCompleted(7L, 31L)).willReturn(List.of(response()));

    mockMvc
        .perform(get("/api/v1/ai/journals/31/image-analysis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].summary").value("잎 끝이 옅게 보여요."));

    verify(analysisService).getCompleted(7L, 31L);
  }

  @Test
  void exposesRetryAfterWhenSameImageIsAlreadyBeingAnalyzed() throws Exception {
    given(analysisService.analyze(eq(7L), eq(31L), eq(IMAGE_HASH)))
        .willThrow(new BusinessException(ErrorCode.AI_IMAGE_ANALYSIS_IN_PROGRESS));

    mockMvc
        .perform(
            post("/api/v1/ai/journals/31/image-analysis")
                .contentType(APPLICATION_JSON)
                .content("{\"imageHash\":\"%s\"}".formatted(IMAGE_HASH)))
        .andExpect(status().isConflict())
        .andExpect(header().string("Retry-After", "2"))
        .andExpect(jsonPath("$.code").value("AI_IMAGE_ANALYSIS_IN_PROGRESS"));
  }

  private JournalImageAnalysisResponse response() {
    return new JournalImageAnalysisResponse(
        91L,
        31L,
        IMAGE_HASH,
        ImageQuality.CLEAR,
        PlantCondition.NEEDS_ATTENTION,
        "잎 끝이 옅게 보여요.",
        List.of("잎 끝 색이 주변보다 옅어요."),
        List.of("물주기 간격의 영향일 수 있어요."),
        List.of("흙이 마른 정도를 확인해 주세요."),
        List.of("잎 뒷면을 살펴봐 주세요."),
        LocalDateTime.of(2026, 8, 13, 10, 30));
  }
}
