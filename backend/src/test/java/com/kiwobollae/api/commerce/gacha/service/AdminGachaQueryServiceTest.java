package com.kiwobollae.api.commerce.gacha.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminGachaQueryServiceTest {

  @Mock private GachaDrawRepository repository;

  @Test
  void filtersAdminHistoryByStatusAndUser() {
    AdminGachaQueryService service = new AdminGachaQueryService(repository);
    given(repository.findAdminHistory(GachaDrawStatus.MANUAL_REVIEW, 7L, PageRequest.of(0, 20)))
        .willReturn(Page.empty(PageRequest.of(0, 20)));

    var response = service.getDraws(GachaDrawStatus.MANUAL_REVIEW, 7L, 0, 20);

    assertThat(response.content()).isEmpty();
    assertThat(response.page()).isZero();
  }

  @Test
  void rejectsInvalidPageParameters() {
    AdminGachaQueryService service = new AdminGachaQueryService(repository);

    assertThatThrownBy(() -> service.getDraws(null, null, -1, 20))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.getDraws(null, null, 0, 101))
        .isInstanceOf(BusinessException.class);
  }
}
