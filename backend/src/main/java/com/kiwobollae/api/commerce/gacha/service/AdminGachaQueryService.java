package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.dto.AdminGachaDrawPageResponse;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminGachaQueryService {

  private static final int MAX_PAGE_SIZE = 100;

  private final GachaDrawRepository gachaDrawRepository;

  public AdminGachaDrawPageResponse getDraws(
      GachaDrawStatus status, Long userId, int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (userId != null && userId < 1)) {
      throw new BusinessException(
          ErrorCode.COMMON_VALIDATION_FAILED,
          Map.of("reason", "page는 0 이상, size는 1~100, userId는 양수여야 합니다."));
    }
    return AdminGachaDrawPageResponse.from(
        gachaDrawRepository.findAdminHistory(status, userId, PageRequest.of(page, size)));
  }
}
