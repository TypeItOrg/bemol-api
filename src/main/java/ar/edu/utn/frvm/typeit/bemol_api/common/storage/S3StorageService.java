package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3StorageService implements StorageService {

  private final S3Client client;
  private final String bucket;
  private final String prefix;

  public S3StorageService(
      final S3Client client,
      @Value("${app.storage.s3.bucket}") final String bucket,
      @Value("${app.storage.s3.prefix}") final String prefix) {
    if (bucket.isBlank()) {
      throw new IllegalStateException(StorageMessages.BUCKET_REQUIRED);
    }

    this.client = client;
    this.bucket = bucket;
    this.prefix = prefix.isBlank() ? "" : prefix.replaceAll("/+$", "") + "/";

    verifyStorageAccess();
  }

  @Override
  public void write(
      final String key,
      final String contentType,
      final long size,
      final InputStreamSource content) {

    try (final var input = content.getInputStream()) {
      client.putObject(
          request -> request.bucket(bucket).key(resolveKey(key)).contentType(contentType),
          RequestBody.fromInputStream(input, size));

    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @Override
  public Resource loadAsResource(final String storagePath) {
    return new InputStreamResource(loadAsInputStream(storagePath));
  }

  @Override
  public InputStream loadAsInputStream(final String storagePath) {
    try {
      return client.getObject(request -> request.bucket(bucket).key(resolveKey(storagePath)));
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) {
        throw new StorageFileNotFoundException();
      }

      throw exception;
    }
  }

  @Override
  public Destination destination() {
    return new Destination("s3", bucket, prefix);
  }

  @Override
  public boolean deletePhysicalFile(final String storagePath) {
    client.deleteObject(request -> request.bucket(bucket).key(resolveKey(storagePath)));
    return true;
  }

  private String resolveKey(final String key) {
    return StorageKey.validate(prefix + StorageKey.validate(key));
  }

  private void verifyStorageAccess() {
    final String probeKey = resolveKey(".storage-check-" + UUID.randomUUID());

    client.putObject(
        request -> request.bucket(bucket).key(probeKey), RequestBody.fromBytes(new byte[] {0}));

    try {
      client.getObjectAsBytes(request -> request.bucket(bucket).key(probeKey));
    } finally {
      client.deleteObject(request -> request.bucket(bucket).key(probeKey));
    }
  }
}
