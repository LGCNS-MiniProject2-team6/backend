package com.recu.user.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.recu.user.common.exception.BusinessException;
import com.recu.user.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleUserInfoClient {

  private static final String GOOGLE_API_BASE_URL = "https://www.googleapis.com";
  private static final String USER_INFO_PATH = "/oauth2/v1/userinfo";
  private static final String TOKEN_INFO_URL =
      "https://oauth2.googleapis.com/tokeninfo?access_token={accessToken}";

  private final RestClient restClient;
  private final String googleClientId;

  public GoogleUserInfoClient(
      RestClient.Builder restClientBuilder,
      @Value("${google.client-id:dummy-google-client-id-for-msa}") String googleClientId) {
    this.restClient = restClientBuilder.baseUrl(GOOGLE_API_BASE_URL).build();
    this.googleClientId = googleClientId;
  }

  public GoogleUserInfo getUserInfo(String googleAccessToken) {
    verifyAudience(googleAccessToken);

    return restClient
        .get()
        .uri(USER_INFO_PATH)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + googleAccessToken)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            (request, response) -> {
              throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
            })
        .body(GoogleUserInfo.class);
  }

  private void verifyAudience(String googleAccessToken) {
    if (googleClientId == null || googleClientId.isBlank() || googleClientId.startsWith("dummy-")) {
      return; // 로컬 진단 환경 등 client-id 미설정 시 통과
    }

    GoogleTokenInfo tokenInfo =
        restClient
            .get()
            .uri(TOKEN_INFO_URL, googleAccessToken)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (request, response) -> {
                  throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
                })
            .body(GoogleTokenInfo.class);

    if (tokenInfo == null || !googleClientId.equals(tokenInfo.audience())) {
      throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GoogleUserInfo(String id, String name, String email) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GoogleTokenInfo(@JsonAlias("aud") String audience) {}
}
