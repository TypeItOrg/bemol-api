package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public record S3StorageProperties(
    String bucket,
    String region,
    String prefix,
    String endpoint,
    String accessKeyId,
    String secretAccessKey,
    String sessionToken) {

  public S3StorageProperties {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException(StorageMessages.BUCKET_REQUIRED);
    }

    prefix = normalizePrefix(prefix);
    endpoint = endpoint == null ? "" : endpoint;
    accessKeyId = accessKeyId == null ? "" : accessKeyId;
    secretAccessKey = secretAccessKey == null ? "" : secretAccessKey;
    sessionToken = sessionToken == null ? "" : sessionToken;
  }

  private static String normalizePrefix(final String prefix) {
    return prefix == null || prefix.isBlank() ? "" : prefix.replaceAll("/+$", "") + "/";
  }
}
