package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.nio.charset.StandardCharsets;

final class StorageKey {
  private static final int MAX_UTF8_BYTES = 1024;

  private StorageKey() {}

  static String validate(final String key) {
    if (key == null || key.isBlank()) {
      throw new InvalidStorageKeyException();
    }

    if (containsForbiddenCharacters(key) || exceedsMaximumLength(key)) {
      throw new InvalidStorageKeyException();
    }

    validatePathSegments(key);
    return key;
  }

  private static boolean containsForbiddenCharacters(final String key) {
    return key.contains("\\") || key.codePoints().anyMatch(Character::isISOControl);
  }

  private static boolean exceedsMaximumLength(final String key) {
    return key.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES;
  }

  private static void validatePathSegments(final String key) {
    for (final String segment : key.split("/", -1)) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new InvalidStorageKeyException();
      }
    }
  }
}
