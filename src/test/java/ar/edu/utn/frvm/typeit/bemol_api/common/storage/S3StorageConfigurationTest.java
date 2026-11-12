package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.auth.scheme.S3AuthSchemeParams;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3StorageConfigurationTest {
  private final S3StorageConfiguration configuration = new S3StorageConfiguration();

  @Test
  void usesOnlyTheGenericRegionSetting() throws IOException {
    final var environment = new MockEnvironment().withProperty("AWS_REGION", "eu-west-1");
    environment
        .getPropertySources()
        .addLast(
            new PropertiesPropertySource(
                "application", PropertiesLoaderUtils.loadAllProperties("application.properties")));

    assertThat(environment.getProperty("app.storage.s3.region")).isEqualTo("us-east-1");

    environment.setProperty("STORAGE_S3_REGION", "auto");

    assertThat(environment.getProperty("app.storage.s3.region")).isEqualTo("auto");
  }

  @Test
  void keepsAwsEndpointDefaultsAndExistingDestination() {
    try (final var client =
        configuration.storageS3Client(
            properties("us-east-1", "", "storage-key", "storage-secret", ""))) {
      final var settings = client.serviceClientConfiguration();

      assertThat(settings.endpointOverride()).isEmpty();
      assertThat(settings.region()).isEqualTo(Region.US_EAST_1);
      assertThat(settings.credentialsProvider()).isInstanceOf(StaticCredentialsProvider.class);
      assertThat(settings.overrideConfiguration().apiCallTimeout()).contains(Duration.ofMinutes(2));
      assertThat(settings.overrideConfiguration().apiCallAttemptTimeout())
          .contains(Duration.ofSeconds(30));
      assertThat(chunkedEncodingEnabled(client)).isTrue();
      assertThat(destination(client))
          .isEqualTo(new StorageService.Destination("s3", "bemol-documents", "bemol/"));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://account.r2.cloudflarestorage.com",
        "https://account.r2.cloudflarestorage.com/"
      })
  void usesR2EndpointWithoutChunkedEncoding(final String endpoint) {
    try (final var client =
        configuration.storageS3Client(
            properties("auto", endpoint, "storage-key", "storage-secret", ""))) {
      final var settings = client.serviceClientConfiguration();

      assertThat(settings.endpointOverride()).contains(URI.create(endpoint));
      assertThat(settings.region()).isEqualTo(Region.of("auto"));
      assertThat(
              client
                  .utilities()
                  .getUrl(request -> request.bucket("bemol-documents").key("document.pdf")))
          .hasToString("https://account.r2.cloudflarestorage.com/bemol-documents/document.pdf");
      assertThat(chunkedEncodingEnabled(client)).isFalse();
      assertThat(destination(client))
          .isEqualTo(
              new StorageService.Destination(
                  "s3", "https://account.r2.cloudflarestorage.com/bemol-documents", "bemol/"));
    }
  }

  @Test
  void distinguishesR2AccountsWithTheSameBucket() {
    try (final var first =
            configuration.storageS3Client(
                properties(
                    "auto",
                    "https://first.r2.cloudflarestorage.com",
                    "storage-key",
                    "storage-secret",
                    ""));
        final var second =
            configuration.storageS3Client(
                properties(
                    "auto",
                    "https://second.r2.cloudflarestorage.com",
                    "storage-key",
                    "storage-secret",
                    ""))) {
      assertThat(destination(first)).isNotEqualTo(destination(second));
    }
  }

  @Test
  void usesExplicitCredentialsWithAnyS3Endpoint() {
    try (final var client =
        configuration.storageS3Client(
            properties(
                "us-east-1", "https://s3.example.invalid", "storage-key", "storage-secret", ""))) {
      final var credentials =
          client.serviceClientConfiguration().credentialsProvider().resolveIdentity().join();

      assertThat(credentials.accessKeyId()).isEqualTo("storage-key");
      assertThat(credentials.secretAccessKey()).isEqualTo("storage-secret");
      assertThat(credentials).isNotInstanceOf(AwsSessionCredentialsIdentity.class);
      assertThat(
              client
                  .utilities()
                  .getUrl(request -> request.bucket("bemol-documents").key("document.pdf")))
          .hasToString("https://s3.example.invalid/bemol-documents/document.pdf");
    }
  }

  @Test
  void usesTemporaryS3Credentials() {
    try (final var client =
        configuration.storageS3Client(
            properties("us-east-1", "", "storage-key", "storage-secret", "storage-session"))) {
      final var credentials =
          client.serviceClientConfiguration().credentialsProvider().resolveIdentity().join();

      assertThat(credentials)
          .isInstanceOfSatisfying(
              AwsSessionCredentialsIdentity.class,
              session -> assertThat(session.sessionToken()).isEqualTo("storage-session"));
    }
  }

  @ParameterizedTest
  @CsvSource({"'','',''", "key,'',''", "'',secret,''", "'','',token"})
  void requiresExplicitS3Credentials(
      final String accessKeyId, final String secretAccessKey, final String sessionToken) {
    assertThatThrownBy(
            () ->
                configuration.storageS3Client(
                    properties("us-east-1", "", accessKeyId, secretAccessKey, sessionToken)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(StorageMessages.S3_CREDENTIALS_REQUIRED);
  }

  private static Boolean chunkedEncodingEnabled(final S3Client client) {
    final var settings = client.serviceClientConfiguration();
    final var options =
        settings
            .authSchemeProvider()
            .resolveAuthScheme(
                S3AuthSchemeParams.builder()
                    .operation("PutObject")
                    .region(settings.region())
                    .bucket("bemol-documents")
                    .endpoint(settings.endpointOverride().map(uri -> uri.toString()).orElse(null))
                    .build());

    return options.getFirst().signerProperty(AwsV4HttpSigner.CHUNK_ENCODING_ENABLED);
  }

  private static StorageService.Destination destination(final S3Client configuredClient) {
    final S3Client client = mock(S3Client.class);
    final S3Presigner presigner = mock(S3Presigner.class);
    when(client.serviceClientConfiguration())
        .thenReturn(configuredClient.serviceClientConfiguration());

    return new S3StorageService(client, presigner, properties()).destination();
  }

  private static S3StorageProperties properties() {
    return properties("us-east-1", "", "storage-key", "storage-secret", "");
  }

  private static S3StorageProperties properties(
      final String region,
      final String endpoint,
      final String accessKeyId,
      final String secretAccessKey,
      final String sessionToken) {
    return new S3StorageProperties(
        "bemol-documents", region, "bemol/", endpoint, accessKeyId, secretAccessKey, sessionToken);
  }
}
