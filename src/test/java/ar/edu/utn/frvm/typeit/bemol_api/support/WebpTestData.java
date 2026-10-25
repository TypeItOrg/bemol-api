package ar.edu.utn.frvm.typeit.bemol_api.support;

import static java.util.Objects.requireNonNull;

import java.io.IOException;

public final class WebpTestData {
  private WebpTestData() {}

  public static byte[] read(final String name) throws IOException {
    try (final var input =
        requireNonNull(WebpTestData.class.getResourceAsStream("/images/webp/" + name))) {
      return input.readAllBytes();
    }
  }
}
