package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService.ObjectMetadata;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidFileException;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class EnrollmentFilePolicy {
  public record StoredFile(
      String safeFileName, String storagePath, String contentType, long size) {}

  public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L;
  private static final int MAX_FILE_NAME_LENGTH = 255;

  private EnrollmentFilePolicy() {}

  public static StoredFile prepare(
      final UUID ownerId,
      final @Nullable String originalFileName,
      final @Nullable String declaredContentType,
      final long fileSize) {
    validateMetadata(originalFileName, declaredContentType, fileSize);

    final String contentType = normalizeContentType(declaredContentType);
    final String extension = extensionFor(contentType);
    final String fileName = UUID.randomUUID() + extension;

    return new StoredFile(
        fileName, "enrollments/" + ownerId + "/" + fileName, contentType, fileSize);
  }

  public static String resolveOriginalFileName(
      final @Nullable String originalFileName, final String fallbackSafeName) {
    return originalFileName == null || originalFileName.isBlank()
        ? fallbackSafeName
        : originalFileName;
  }

  public static String extensionFor(final String contentType) {
    return switch (contentType) {
      case "application/pdf" -> ".pdf";
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      default -> throw new InvalidFileException(EnrollmentMessages.FILE_TYPE_INVALID);
    };
  }

  public static void verifyUploadedObject(
      final ObjectMetadata actual, final String expectedContentType, final long expectedSize) {
    if (actual.contentLength() > MAX_FILE_SIZE_BYTES) {
      throw new InvalidFileException(EnrollmentMessages.FILE_TOO_LARGE);
    }
    if (actual.contentLength() <= 0 || actual.contentLength() != expectedSize) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }

    final String actualType = normalizeContentType(actual.contentType());
    if (!expectedContentType.equals(actualType)) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
  }

  private static void validateMetadata(
      final @Nullable String originalName,
      final @Nullable String contentType,
      final long fileSize) {
    if (fileSize <= 0) {
      throw new InvalidFileException(EnrollmentMessages.FILE_EMPTY);
    }
    if (fileSize > MAX_FILE_SIZE_BYTES) {
      throw new InvalidFileException(EnrollmentMessages.FILE_TOO_LARGE);
    }
    if (originalName != null && originalName.length() > MAX_FILE_NAME_LENGTH) {
      throw new InvalidFileException(EnrollmentMessages.ATTACHMENT_NAME_TOO_LONG);
    }
    if (originalName != null && originalName.chars().anyMatch(Character::isISOControl)) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
    if (contentType == null || contentType.isBlank()) {
      throw new InvalidFileException(EnrollmentMessages.FILE_TYPE_REQUIRED);
    }
  }

  private static String normalizeContentType(final @Nullable String rawType) {
    if (rawType == null || rawType.isBlank()) {
      throw new InvalidFileException(EnrollmentMessages.FILE_TYPE_REQUIRED);
    }
    final int semicolon = rawType.indexOf(';');
    final String baseType =
        (semicolon >= 0 ? rawType.substring(0, semicolon) : rawType)
            .trim()
            .toLowerCase(Locale.ROOT);
    final String normalized = "image/jpg".equals(baseType) ? "image/jpeg" : baseType;
    extensionFor(normalized);
    return normalized;
  }
}
