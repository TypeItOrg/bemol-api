package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.nio.charset.StandardCharsets;

final class StorageKey {
  private StorageKey() {}

  static String validate(final String key) {
    if (key == null
        || key.isBlank()
        || key.contains("\\")
        || key.codePoints().anyMatch(Character::isISOControl)
        || key.getBytes(StandardCharsets.UTF_8).length > 1024) {
      throw new InvalidStorageKeyException();
    }
    for (final String segment : key.split("/", -1)) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new InvalidStorageKeyException();
      }
    }
    return key;
  }
}
