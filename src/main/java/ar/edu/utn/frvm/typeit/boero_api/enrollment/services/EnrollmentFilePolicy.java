package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.web.multipart.MultipartFile;

public final class EnrollmentFilePolicy {
  public record StoredFile(
      String safeFileName, String storagePath, String contentType, long size) {}

  public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L;
  private static final long MAX_IMAGE_PIXELS = 25_000_000L;
  private static final int MAX_FILE_NAME_LENGTH = 255;
  private static final long MAX_PDF_DECODED_BYTES = 64 * 1024 * 1024L;
  private static final int MAX_PDF_PAGES = 1000;

  private EnrollmentFilePolicy() {}

  public static StoredFile prepare(final UUID applicationId, final MultipartFile file) {
    validateMetadata(file);

    final byte[] bytes = readContent(file);
    final String declaredType =
        requireNonNull(file.getContentType()).trim().toLowerCase(Locale.ROOT);
    final String contentType = validateContent(bytes);
    final String normalizedDeclaredType =
        "image/jpg".equals(declaredType) ? "image/jpeg" : declaredType;
    if (!contentType.equals(normalizedDeclaredType)) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }

    final String extension =
        switch (contentType) {
          case "application/pdf" -> ".pdf";
          case "image/jpeg" -> ".jpg";
          case "image/png" -> ".png";
          default -> throw new InvalidFileException(EnrollmentMessages.FILE_TYPE_INVALID);
        };
    final String fileName = UUID.randomUUID() + extension;

    return new StoredFile(
        fileName, "enrollments/" + applicationId + "/" + fileName, contentType, bytes.length);
  }

  private static byte[] readContent(final MultipartFile file) {
    final byte[] bytes;
    try (final var input = file.getInputStream()) {
      bytes = input.readNBytes((int) MAX_FILE_SIZE_BYTES + 1);
    } catch (IOException exception) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }

    if (bytes.length > MAX_FILE_SIZE_BYTES) {
      throw new InvalidFileException(EnrollmentMessages.FILE_TOO_LARGE);
    }
    if (bytes.length == 0 || bytes.length != file.getSize()) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }

    return bytes;
  }

  private static void validateMetadata(final MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidFileException(EnrollmentMessages.FILE_EMPTY);
    }
    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new InvalidFileException(EnrollmentMessages.FILE_TOO_LARGE);
    }
    final String originalName = file.getOriginalFilename();
    if (originalName != null && originalName.length() > MAX_FILE_NAME_LENGTH) {
      throw new InvalidFileException(EnrollmentMessages.ATTACHMENT_NAME_TOO_LONG);
    }
    if (originalName != null && originalName.chars().anyMatch(Character::isISOControl)) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
    if (file.getContentType() == null || file.getContentType().isBlank()) {
      throw new InvalidFileException(EnrollmentMessages.FILE_TYPE_REQUIRED);
    }
  }

  private static String validateContent(final byte[] bytes) {
    try {
      if (bytes.length >= 5 && "%PDF-".equals(new String(bytes, 0, 5, StandardCharsets.US_ASCII))) {
        validatePdf(bytes);
        return "application/pdf";
      }
      return validateImage(bytes);
    } catch (InvalidPasswordException exception) {
      throw new InvalidFileException(EnrollmentMessages.FILE_PDF_ENCRYPTED);
    } catch (IOException | IllegalArgumentException exception) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
  }

  private static void validatePdf(final byte[] bytes) throws IOException {
    final int tail = Math.max(0, bytes.length - 1024);
    if (!new String(bytes, tail, bytes.length - tail, StandardCharsets.US_ASCII)
        .stripTrailing()
        .endsWith("%%EOF")) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
    try (final var source = new RandomAccessReadBuffer(bytes);
        final var parser = new EnrollmentPdfParser(source, MAX_PDF_DECODED_BYTES);
        final var document = parser.parse(false)) {
      if (document.isEncrypted()) {
        throw new InvalidFileException(EnrollmentMessages.FILE_PDF_ENCRYPTED);
      }
      if (document.getNumberOfPages() < 1 || document.getNumberOfPages() > MAX_PDF_PAGES) {
        throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
      }
      long decodedBytes = 0;
      final byte[] buffer = new byte[8192];
      for (final var page : document.getPages()) {
        try (final var content = page.getContents()) {
          int read;
          while ((read = content.read(buffer)) != -1) {
            decodedBytes += read;
            if (decodedBytes > MAX_PDF_DECODED_BYTES) {
              throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
            }
          }
        }
      }
    }
  }

  private static String validateImage(final byte[] bytes) throws IOException {
    try (final var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      final var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
      }
      final var reader = readers.next();
      try {
        final String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!format.equals("jpeg") && !format.equals("png")) {
          throw new InvalidFileException(EnrollmentMessages.FILE_TYPE_INVALID);
        }
        validateImageTrailer(bytes, format);

        reader.setInput(input, true, true);
        validateImageDimensions(reader);
        validateDecodedImage(reader);

        return format.equals("jpeg") ? "image/jpeg" : "image/png";
      } finally {
        reader.dispose();
      }
    }
  }

  private static void validateImageTrailer(final byte[] bytes, final String format) {
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
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
  }

  private static void validateImageDimensions(final ImageReader reader) throws IOException {
    final int width = reader.getWidth(0);
    final int height = reader.getHeight(0);
    if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
      throw new InvalidFileException(EnrollmentMessages.FILE_IMAGE_TOO_LARGE);
    }
  }

  private static void validateDecodedImage(final ImageReader reader) throws IOException {
    final var warning = new AtomicBoolean();
    reader.addIIOReadWarningListener((source, message) -> warning.set(true));

    final var image = reader.read(0);
    if (image == null || warning.get()) {
      throw new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
    }
    image.flush();
  }
}
