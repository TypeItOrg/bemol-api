package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService.ObjectMetadata;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionLogoTooLargeException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InvalidInstitutionLogoException;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class InstitutionLogoPolicy {
  public static final long MAX_BYTES = 2 * 1024 * 1024L;

  public record PreparedLogo(String version, String key, String contentType, long size) {}

  private InstitutionLogoPolicy() {}

  public static PreparedLogo prepare(
      final UUID institutionId,
      final @Nullable String version,
      final @Nullable String declaredContentType,
      final long fileSize) {
    validateSize(fileSize);
    final String contentType = normalizeContentType(declaredContentType);
    final String resolvedVersion = resolveVersion(version);
    final String key =
        "institutions/" + institutionId + "/logos/" + resolvedVersion + extensionFor(contentType);

    return new PreparedLogo(resolvedVersion, key, contentType, fileSize);
  }

  public static void verifyUploadedObject(
      final ObjectMetadata actual, final String expectedContentType, final long expectedSize) {
    validateSize(actual.contentLength());
    if (actual.contentLength() != expectedSize) {
      throw new InvalidInstitutionLogoException();
    }
    final String actualType = normalizeContentType(actual.contentType());
    if (!expectedContentType.equals(actualType)) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static void validateSize(final long fileSize) {
    if (fileSize > MAX_BYTES) {
      throw new InstitutionLogoTooLargeException();
    }
    if (fileSize <= 0) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static String resolveVersion(final @Nullable String version) {
    if (version == null) {
      return UUID.randomUUID().toString();
    }
    try {
      return UUID.fromString(version).toString();
    } catch (IllegalArgumentException exception) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static String normalizeContentType(final @Nullable String rawType) {
    if (rawType == null || rawType.isBlank()) {
      throw new InvalidInstitutionLogoException();
    }
    final int semicolon = rawType.indexOf(';');
    final String baseType =
        (semicolon >= 0 ? rawType.substring(0, semicolon) : rawType)
            .trim()
            .toLowerCase(Locale.ROOT);
    extensionFor(baseType);
    return baseType;
  }

  private static String extensionFor(final String contentType) {
    return switch (contentType) {
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "image/jpeg" -> ".jpg";
      default -> throw new InvalidInstitutionLogoException();
    };
  }
}
