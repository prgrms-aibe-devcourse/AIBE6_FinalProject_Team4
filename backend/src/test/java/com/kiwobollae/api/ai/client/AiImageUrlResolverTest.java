package com.kiwobollae.api.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.ai.config.AiImageProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class AiImageUrlResolverTest {

  private static final String JOURNAL_IMAGE_PATH = "/api/v1/journals/images/12/image.jpg";

  @Test
  void resolvesStoredJournalPathToPublicAbsoluteUrl() {
    AiImageUrlResolver resolver = resolver("https://api.kiwor.site");

    String absoluteUrl = resolver.resolveJournalImageUrl(JOURNAL_IMAGE_PATH);

    assertThat(absoluteUrl).isEqualTo("https://api.kiwor.site" + JOURNAL_IMAGE_PATH);
  }

  // 배포 워크플로가 AI_IMAGE_PUBLIC_BASE_URL을 "https://${APP_DOMAIN}"으로 주입하므로,
  // APP_DOMAIN 값에 따라 끝 슬래시나 포트가 섞여 들어올 수 있다. 세 형태 모두 같은 결과여야 한다.
  @Test
  void acceptsPublicBaseUrlWithTrailingSlashOrPort() {
    assertThat(resolver("https://api.kiwor.site/").resolveJournalImageUrl(JOURNAL_IMAGE_PATH))
        .isEqualTo("https://api.kiwor.site" + JOURNAL_IMAGE_PATH);
    assertThat(resolver("https://api.kiwor.site:8443").resolveJournalImageUrl(JOURNAL_IMAGE_PATH))
        .isEqualTo("https://api.kiwor.site:8443" + JOURNAL_IMAGE_PATH);
    assertThat(resolver("HTTPS://api.kiwor.site").resolveJournalImageUrl(JOURNAL_IMAGE_PATH))
        .isEqualTo("HTTPS://api.kiwor.site" + JOURNAL_IMAGE_PATH);
  }

  @Test
  void rejectsUrlsOutsideJournalImageProxy() {
    AiImageUrlResolver resolver = resolver("https://api.kiwor.site");

    assertThatThrownBy(
            () -> resolver.resolveJournalImageUrl("https://images.example.com/image.jpg"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver.resolveJournalImageUrl("//images.example.com/image.jpg"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> resolver.resolveJournalImageUrl("/api/v1/journals/images/12/../secret.jpg"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver.resolveJournalImageUrl("/api/v1/plants/images/12/image.jpg"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMissingOrUnsafePublicBaseUrl() {
    assertConfigurationError("");
    assertConfigurationError("http://api.kiwor.site");
    assertConfigurationError("https://user@api.kiwor.site");
    assertConfigurationError("https://api.kiwor.site/internal");
  }

  private void assertConfigurationError(String publicBaseUrl) {
    assertThatThrownBy(
            () ->
                resolver(publicBaseUrl)
                    .resolveJournalImageUrl("/api/v1/journals/images/12/image.jpg"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_CONFIGURATION_INVALID));
  }

  private AiImageUrlResolver resolver(String publicBaseUrl) {
    return new AiImageUrlResolver(new AiImageProperties(publicBaseUrl));
  }
}
