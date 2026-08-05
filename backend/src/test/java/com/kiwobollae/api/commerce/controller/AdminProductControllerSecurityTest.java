package com.kiwobollae.api.commerce.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminProductControllerSecurityTest {

  @Test
  void requiresAdminRoleAtControllerLevel() {
    PreAuthorize authorization =
        AnnotatedElementUtils.findMergedAnnotation(
            AdminProductController.class, PreAuthorize.class);

    assertThat(authorization).isNotNull();
    assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
  }
}
