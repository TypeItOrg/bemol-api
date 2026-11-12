package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

  private final S3Client client;
  private final S3Presigner presigner;
  private final S3StorageProperties properties;

  @Override
  public PresignedUpload presignUpload(
      final String key, final String contentType, final long size, final Duration ttl) {
    final var request = uploadRequest(key, contentType, size);
    final var presigned =
        presigner.presignPutObject(
            builder -> builder.signatureDuration(ttl).putObjectRequest(request));

    return new PresignedUpload(
        presigned.url().toString(), presigned.expiration(), Map.of("Content-Type", contentType));
  }

  @Override
  public PresignedDownload presignDownload(
      final String storagePath,
      final String responseContentType,
      final @Nullable String fileName,
      final Duration ttl) {
    final var request = downloadRequest(storagePath, responseContentType, fileName);
    final var presigned =
        presigner.presignGetObject(
            builder -> builder.signatureDuration(ttl).getObjectRequest(request));

    return new PresignedDownload(presigned.url().toString(), presigned.expiration());
  }

  @Override
  public ObjectMetadata headObject(final String storagePath) {
    try {
      final var response =
          client.headObject(
              request -> request.bucket(properties.bucket()).key(resolveKey(storagePath)));

      return objectMetadata(response);
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) {
        throw new StorageFileNotFoundException();
      }

      throw exception;
    }
  }

  @Override
  public Destination destination() {
    return new Destination("s3", storageLocation(), properties.prefix());
  }

  @Override
  public boolean deletePhysicalFile(final String storagePath) {
    client.deleteObject(
        request -> request.bucket(properties.bucket()).key(resolveKey(storagePath)));
    return true;
  }

  private PutObjectRequest uploadRequest(
      final String key, final String contentType, final long size) {
    return PutObjectRequest.builder()
        .bucket(properties.bucket())
        .key(resolveKey(key))
        .contentType(contentType)
        .contentLength(size)
        .build();
  }

  private GetObjectRequest downloadRequest(
      final String storagePath, final String responseContentType, final @Nullable String fileName) {
    return GetObjectRequest.builder()
        .bucket(properties.bucket())
        .key(resolveKey(storagePath))
        .responseContentType(responseContentType)
        .responseContentDisposition(contentDisposition(fileName))
        .build();
  }

  private static String contentDisposition(final @Nullable String fileName) {
    if (fileName == null) {
      return "inline";
    }

    return ContentDisposition.inline()
        .filename(fileName, StandardCharsets.UTF_8)
        .build()
        .toString();
  }

  private static ObjectMetadata objectMetadata(final HeadObjectResponse response) {
    final String contentType =
        response.contentType() == null ? "application/octet-stream" : response.contentType();
    final long contentLength = response.contentLength() == null ? 0L : response.contentLength();

    return new ObjectMetadata(contentType, contentLength);
  }

  private String storageLocation() {
    return client
        .serviceClientConfiguration()
        .endpointOverride()
        .map(endpoint -> endpoint.toString().replaceAll("/+$", "") + "/" + properties.bucket())
        .orElse(properties.bucket());
  }

  private String resolveKey(final String key) {
    return StorageKey.validate(properties.prefix() + StorageKey.validate(key));
  }
}
