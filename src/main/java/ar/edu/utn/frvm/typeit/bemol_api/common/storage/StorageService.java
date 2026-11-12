package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface StorageService {
  record Destination(String provider, String location, String prefix) {}

  record PresignedUpload(String url, Instant expiresAt, Map<String, String> headers) {}

  record PresignedDownload(String url, Instant expiresAt) {}

  record ObjectMetadata(String contentType, long contentLength) {}

  Destination destination();

  PresignedUpload presignUpload(String key, String contentType, long size, Duration ttl);

  PresignedDownload presignDownload(
      String key, String responseContentType, @Nullable String fileName, Duration ttl);

  ObjectMetadata headObject(String key);

  boolean deletePhysicalFile(String key);
}
