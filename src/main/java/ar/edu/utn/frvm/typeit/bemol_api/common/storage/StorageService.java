package ar.edu.utn.frvm.typeit.bemol_api.common.storage;

import java.io.InputStream;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;

/** One application-wide backend. Callers own authorization, validation and object keys. */
public interface StorageService {
  record Destination(String provider, String location, String prefix) {}

  Destination destination();

  void write(String key, String contentType, long size, InputStreamSource content);

  Resource loadAsResource(String key);

  InputStream loadAsInputStream(String key);

  boolean deletePhysicalFile(String key);
}
