package ar.edu.utn.frvm.typeit.bemol_api.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.springframework.mock.web.MockMultipartFile;

public final class EnrollmentDocumentTestData {
  private EnrollmentDocumentTestData() {}

  public static MockMultipartFile pdf(String name) {
    return new MockMultipartFile(
        "file", name, "application/pdf", "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
  }

  public static MockMultipartFile png(String name) throws IOException {
    var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    try (var output = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", output);
      return new MockMultipartFile("file", name, "image/png", output.toByteArray());
    } finally {
      image.flush();
    }
  }
}
