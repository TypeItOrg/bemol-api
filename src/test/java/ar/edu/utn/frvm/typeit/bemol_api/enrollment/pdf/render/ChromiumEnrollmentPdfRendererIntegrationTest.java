package ar.edu.utn.frvm.typeit.bemol_api.enrollment.pdf.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@IntegrationTest
class ChromiumEnrollmentPdfRendererIntegrationTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @CsvSource({"0,0,1", "240,60,80", "60,240,80"})
  void realBrowserProducesReadableMultipageA4Pdf(
      final int logoWidth, final int logoHeight, final int selectionCount) throws Exception {
    final String executable =
        System.getenv()
            .getOrDefault("BEMOL_ENROLLMENT_PDF_CHROMIUM_EXECUTABLE", "/opt/chrome/chrome");
    assumeTrue(Files.isExecutable(Path.of(executable)), "A sandbox-capable Chromium is required");
    final var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setCharacterEncoding("UTF-8");
    final var templates = new SpringTemplateEngine();
    templates.setTemplateResolver(resolver);
    final var renderer =
        new ChromiumEnrollmentPdfRenderer(templates, executable, Duration.ofSeconds(10), temporary);
    final var selections =
        IntStream.rangeClosed(1, selectionCount)
            .mapToObj(
                index ->
                    new EnrollmentPdfData.Selection(
                        "Selección " + index + " — Práctica y teoría del lenguaje musical",
                        "Nivel 2",
                        "Piano",
                        "Plan de estudios 2026",
                        "Solicitada"))
            .toList();
    final var settings =
        new EnrollmentPdfSettingsResponse(
            "Solicitud de inscripción",
            "Conservatorio de música\nDirección de educación",
            "Información de ejemplo con acentos: Córdoba, música, año y ñ. ".repeat(12),
            "Presentar la solicitud firmada.",
            true,
            true);
    final var data =
        EnrollmentPdfData.builder()
            .institution(
                new EnrollmentPdfData.InstitutionDetails(
                    "Conservatorio de Música Ñandú",
                    "Bv. España 123 — Villa María, Córdoba",
                    "0353 1234567",
                    "contacto@example.test"))
            .generatedAt(Instant.parse("2026-11-05T12:00:00Z"))
            .settings(settings)
            .sections(
                List.of(
                    new EnrollmentPdfData.Section(
                        "Datos del estudiante",
                        List.of(
                            new EnrollmentPdfData.Field("Nombre completo", "María Ñandú"),
                            new EnrollmentPdfData.Field("Teléfono", "Sin informar"))),
                    new EnrollmentPdfData.Section(
                        "Preferencias",
                        List.of(new EnrollmentPdfData.Field("Autoriza imágenes", "No")))))
            .selections(selections)
            .coursesSelected(selectionCount > 1)
            .hasResponsible(true)
            .logoDataUri(logoWidth == 0 ? null : logo(logoWidth, logoHeight))
            .build();

    final var evidence = Path.of("build/reports/enrollment-pdf");
    Files.createDirectories(evidence);
    Files.writeString(
        evidence.resolve("example-" + logoWidth + "x" + logoHeight + ".html"), renderer.html(data));

    final var bytes = renderer.render(data);

    Files.write(evidence.resolve("example-" + logoWidth + "x" + logoHeight + ".pdf"), bytes);

    try (final var pdf = Loader.loadPDF(bytes)) {
      assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(selectionCount == 1 ? 1 : 3);
      for (final var page : pdf.getPages()) {
        assertThat(page.getMediaBox().getWidth()).isBetween(594f, 596f);
        assertThat(page.getMediaBox().getHeight()).isBetween(841f, 843f);
      }
      final var text = new PDFTextStripper().getText(pdf);
      assertThat(text)
          .contains("María Ñandú", "Sin informar", "No")
          .contains("Selección " + selectionCount, "Firma y aclaración del responsable")
          .contains(selectionCount == 1 ? "Espacios solicitados" : "Cursos solicitados", "Nivel 2")
          .doesNotContain("Referencia", "Estado de la solicitud");
    }
    try (final var paths = Files.list(temporary)) {
      assertThat(paths.toList()).isEmpty();
    }
  }

  private static String logo(final int width, final int height) throws Exception {
    final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    try {
      graphics.setColor(new Color(114, 33, 53));
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    final var output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
  }
}
