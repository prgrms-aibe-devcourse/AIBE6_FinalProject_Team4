package com.kiwobollae.api.commerce.gacha.controller;

import com.kiwobollae.api.commerce.gacha.dto.GachaShardWalletResponse;
import com.kiwobollae.api.commerce.gacha.service.GachaQaShardService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local QA endpoint. Delete this class after shard/cosmetic QA is complete. */
@RestController
@Profile("local")
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/card/gacha/me/qa")
@ConditionalOnProperty(prefix = "app.qa.gacha-shards", name = "enabled", havingValue = "true")
public class GachaQaController {

  private final GachaQaShardService gachaQaShardService;

  @PostMapping("/shards")
  public ResponseEntity<ApiResponse<GachaShardWalletResponse>> grantShards(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.success(gachaQaShardService.grant(userId)));
  }
}
