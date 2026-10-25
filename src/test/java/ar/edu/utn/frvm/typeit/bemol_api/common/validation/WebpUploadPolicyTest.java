package ar.edu.utn.frvm.typeit.bemol_api.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidFileException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFilePolicy;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionLogoTooLargeException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InvalidInstitutionLogoException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.InstitutionLogoPolicy;
import ar.edu.utn.frvm.typeit.bemol_api.support.WebpTestData;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class WebpUploadPolicyTest {
  @ParameterizedTest
  @ValueSource(strings = {"lossy.webp", "lossless-alpha.webp"})
  void acceptsStaticWebpAndPreservesItsBytes(final String name) throws IOException {
    final var bytes = WebpTestData.read(name);
    final var file = file(bytes, "image/webp");

    final var logo = InstitutionLogoPolicy.prepare(file);
    final var document = EnrollmentFilePolicy.prepare(UUID.randomUUID(), file);

    assertThat(logo.contentType()).isEqualTo("image/webp");
    assertThat(logo.bytes()).isEqualTo(bytes);
    assertThat(document.contentType()).isEqualTo("image/webp");
    assertThat(document.safeFileName()).endsWith(".webp");
    assertThat(document.storagePath()).endsWith(".webp");
    assertThat(document.size()).isEqualTo(bytes.length);
  }

  @Test
  void rejectsMimeSpoofing() throws IOException {
    rejects(file(WebpTestData.read("lossy.webp"), "image/png"));
    rejects(file("<html>not an image</html>".getBytes(StandardCharsets.US_ASCII), "image/webp"));
  }

  @Test
  void rejectsTruncationTrailingBytesInvalidRiffLengthAndCorruptImageData() throws IOException {
    final var original = WebpTestData.read("lossy.webp");
    rejects(file(Arrays.copyOf(original, original.length - 1), "image/webp"));
    rejects(file(Arrays.copyOf(original, original.length + 1), "image/webp"));

    final var invalidLength = original.clone();
    ByteBuffer.wrap(invalidLength).order(ByteOrder.LITTLE_ENDIAN).putInt(4, -1);
    rejects(file(invalidLength, "image/webp"));

    final var corrupt = original.clone();
    Arrays.fill(corrupt, 20, corrupt.length, (byte) 0);
    rejects(file(corrupt, "image/webp"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"animated.webp", "oversized.webp"})
  void rejectsAnimationsAndExcessiveDimensions(final String name) throws IOException {
    rejects(file(WebpTestData.read(name), "image/webp"));
  }

  @Test
  void retainsEmptyAndByteSizeLimits() {
    rejects(file(new byte[0], "image/webp"));
    assertThatThrownBy(
            () ->
                InstitutionLogoPolicy.prepare(
                    file(new byte[(int) InstitutionLogoPolicy.MAX_BYTES + 1], "image/webp")))
        .isInstanceOf(InstitutionLogoTooLargeException.class);
    assertThatThrownBy(
            () ->
                EnrollmentFilePolicy.prepare(
                    UUID.randomUUID(),
                    file(
                        new byte[(int) EnrollmentFilePolicy.MAX_FILE_SIZE_BYTES + 1],
                        "image/webp")))
        .isInstanceOf(InvalidFileException.class);
  }

  private static MockMultipartFile file(final byte[] bytes, final String contentType) {
    return new MockMultipartFile("file", "upload.webp", contentType, bytes);
  }

  private static void rejects(final MockMultipartFile file) {
    assertThatThrownBy(() -> InstitutionLogoPolicy.prepare(file))
        .isInstanceOf(InvalidInstitutionLogoException.class);
    assertThatThrownBy(() -> EnrollmentFilePolicy.prepare(UUID.randomUUID(), file))
        .isInstanceOf(InvalidFileException.class);
  }
}
