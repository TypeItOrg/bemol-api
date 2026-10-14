package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionLogoTooLargeException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InvalidInstitutionLogoException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;

public final class InstitutionLogoPolicy {
  public static final long MAX_BYTES = 2 * 1024 * 1024L;
  private static final long MAX_PIXELS = 4_000_000L;

  public record Image(byte[] bytes, String contentType) {}

  private InstitutionLogoPolicy() {}

  public static Image prepare(final MultipartFile file) {
    validateMetadata(file);

    final byte[] bytes = readContent(file);
    final String contentType = detectContentType(bytes);
    if (!contentType.equals(file.getContentType())) {
      throw new InvalidInstitutionLogoException();
    }

    return new Image(bytes, contentType);
  }

  private static void validateMetadata(final MultipartFile file) {
    if (file.getSize() > MAX_BYTES) {
      throw new InstitutionLogoTooLargeException();
    }
    if (file.isEmpty()) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static byte[] readContent(final MultipartFile file) {
    try (final var stream = file.getInputStream()) {
      final byte[] bytes = stream.readNBytes((int) MAX_BYTES + 1);
      validateContentLength(bytes, file.getSize());

      return bytes;
    } catch (IOException | IllegalArgumentException exception) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static void validateContentLength(final byte[] bytes, final long declaredSize) {
    // Check the actual content too: the declared multipart size is not trusted.
    if (bytes.length > MAX_BYTES) {
      throw new InstitutionLogoTooLargeException();
    }
    if (bytes.length == 0 || bytes.length != declaredSize) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static String detectContentType(final byte[] bytes) {
    try (final var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      final var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new InvalidInstitutionLogoException();
      }

      final var reader = readers.next();
      try {
        final String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        validateFormatAndTrailer(bytes, format);

        reader.setInput(input, true, true);
        validateDimensions(reader);
        validateDecodedImage(reader);

        return format.equals("png") ? "image/png" : "image/jpeg";
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException exception) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static void validateFormatAndTrailer(final byte[] bytes, final String format) {
    final boolean complete =
        switch (format) {
          case "jpeg" ->
              bytes.length >= 2
                  && bytes[bytes.length - 2] == (byte) 0xff
                  && bytes[bytes.length - 1] == (byte) 0xd9;
          case "png" ->
              bytes.length >= 12
                  && "IEND"
                      .equals(new String(bytes, bytes.length - 8, 4, StandardCharsets.US_ASCII));
          default -> false;
        };
    if (!complete) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static void validateDimensions(final ImageReader reader) throws IOException {
    final int width = reader.getWidth(0);
    final int height = reader.getHeight(0);
    if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
      throw new InvalidInstitutionLogoException();
    }
  }

  private static void validateDecodedImage(final ImageReader reader) throws IOException {
    final var warning = new AtomicBoolean();
    reader.addIIOReadWarningListener((source, message) -> warning.set(true));

    final var image = reader.read(0);
    if (image == null || warning.get()) {
      throw new InvalidInstitutionLogoException();
    }
    image.flush();
  }
}
