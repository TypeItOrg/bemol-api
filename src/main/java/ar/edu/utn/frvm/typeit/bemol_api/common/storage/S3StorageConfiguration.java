package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3StorageConfiguration {
  @Bean(destroyMethod = "close")
  public S3Client storageS3Client(final S3StorageProperties properties) {
    final var builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    credentials(
                        properties.accessKeyId(),
                        properties.secretAccessKey(),
                        properties.sessionToken())))
            .overrideConfiguration(
                configuration ->
                    configuration
                        .apiCallTimeout(Duration.ofMinutes(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30)));

    if (!properties.endpoint().isBlank()) {
      builder
          .endpointOverride(URI.create(properties.endpoint()))
          .serviceConfiguration(
              configuration ->
                  configuration.pathStyleAccessEnabled(true).chunkedEncodingEnabled(false));
    }

    return builder.build();
  }

  @Bean(destroyMethod = "close")
  public S3Presigner storageS3Presigner(final S3StorageProperties properties) {
    final var builder =
        S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    credentials(
                        properties.accessKeyId(),
                        properties.secretAccessKey(),
                        properties.sessionToken())));

    if (!properties.endpoint().isBlank()) {
      builder
          .endpointOverride(URI.create(properties.endpoint()))
          .serviceConfiguration(
              S3Configuration.builder()
                  .pathStyleAccessEnabled(true)
                  .chunkedEncodingEnabled(false)
                  .build());
    }

    return builder.build();
  }

  private static AwsCredentials credentials(
      final String accessKeyId, final String secretAccessKey, final String sessionToken) {
    if (accessKeyId.isBlank() || secretAccessKey.isBlank()) {
      throw new IllegalStateException(StorageMessages.S3_CREDENTIALS_REQUIRED);
    }

    return sessionToken.isBlank()
        ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
        : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
  }
}
