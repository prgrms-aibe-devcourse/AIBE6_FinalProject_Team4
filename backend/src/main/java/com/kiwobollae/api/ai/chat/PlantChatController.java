package com.kiwobollae.api.ai.chat;

import com.kiwobollae.api.ai.chat.dto.PlantChatRequest;
import com.kiwobollae.api.ai.chat.dto.PlantChatResponse;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 식물 챗봇", description = "식물 프로필과 성장 기록을 바탕으로 한 질문 답변 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/ai/plant-profiles")
public class PlantChatController {

  private final PlantChatService plantChatService;

  @Operation(
      summary = "식물 프로필별 AI 질문 답변",
      description = "식물 종·프로필·최근 일지와 클라이언트가 전달한 최근 대화를 근거로 답변합니다. " + "대화 및 작성 중인 일지는 저장하지 않습니다.")
  @PostMapping("/{profileId}/chat")
  public ResponseEntity<ApiResponse<PlantChatResponse>> chat(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long profileId,
      @Valid @RequestBody PlantChatRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(plantChatService.chat(userId, profileId, request)));
  }
}
