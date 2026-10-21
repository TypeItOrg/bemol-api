package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
  private final Path baseDir;

  public LocalStorageService(@Value("${app.storage.base-dir}") final String baseDir) {
    this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.baseDir);
      final Path probe = Files.createTempFile(this.baseDir, ".storage-check-", ".tmp");
      try {
        Files.writeString(probe, "storage-check");
        Files.readString(probe);
      } finally {
        Files.deleteIfExists(probe);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(StorageMessages.UNAVAILABLE, exception);
    }
  }

  @Override
  public void write(
      final String key,
      final String contentType,
      final long size,
      final InputStreamSource content) {
    final Path file = resolve(key);
    try {
      Files.createDirectories(file.getParent());
      try (final InputStream input = content.getInputStream()) {
        Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @Override
  public Resource loadAsResource(final String key) {
    return new FileSystemResource(readablePath(key));
  }

  @Override
  public InputStream loadAsInputStream(final String key) {
    final Path file = readablePath(key);
    try {
      return Files.newInputStream(file);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @Override
  public Destination destination() {
    return new Destination("local", baseDir.toString(), "");
  }

  @Override
  public boolean deletePhysicalFile(final String key) {
    final Path file = resolve(key);
    try {
      Files.deleteIfExists(file);
      return true;
    } catch (IOException exception) {
      throw new UncheckedIOException(StorageMessages.DELETE_FAILED, exception);
    }
  }

  private Path readablePath(final String key) {
    final Path file = resolve(key);
    if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
      throw new StorageFileNotFoundException();
    }
    return file;
  }

  private Path resolve(final String key) {
    final Path file = baseDir.resolve(StorageKey.validate(key)).normalize();
    if (!file.startsWith(baseDir) || file.equals(baseDir)) {
      throw new InvalidStorageKeyException();
    }
    return file;
  }
}
