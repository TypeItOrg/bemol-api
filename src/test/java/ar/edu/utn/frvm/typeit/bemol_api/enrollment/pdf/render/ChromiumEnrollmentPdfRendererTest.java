package ar.edu.utn.frvm.typeit.bemol_api.enrollment.pdf.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class ChromiumEnrollmentPdfRendererTest {
  @TempDir Path temporary;

  @Test
  void templateEscapesTextEmbedsFontAndOmitsAbsentLogoAndResponsibleSignature() throws Exception {
    final var renderer = renderer("/missing", Duration.ofSeconds(1));
    final var data =
        data().toBuilder()
            .settings(
                new EnrollmentPdfSettingsResponse(
                    "<script>título</script>",
                    "<img src=x>",
                    "Línea uno\nLínea dos",
                    null,
                    true,
                    true))
            .build();

    final var html = renderer.html(data);

    assertThat(html)
        .contains(
            "&lt;script&gt;título&lt;/script&gt;", "&lt;img src=x&gt;", "Línea uno\nLínea dos")
        .contains("data:font/woff2;base64,", "script-src 'none'", "connect-src 'none'")
        .contains("Córdoba", "123", "contacto@example.test")
        .contains(
            "No", "05/11/2026 09:00", "Fecha de emisión", "Firma y aclaración del solicitante")
        .doesNotContain(
            "<script>",
            "<img ",
            " · ",
            "Firma y aclaración del responsable",
            "Referencia",
            "Fecha de presentación",
            "Estado de la solicitud");
  }

  @Test
  void templateUsesTheSelectionTypeForItsTitleAndColumn() throws Exception {
    final var renderer = renderer("/missing", Duration.ofSeconds(1));

    assertThat(renderer.html(data()))
        .contains("Espacios solicitados", ">Espacio académico</th>")
        .doesNotContain("Espacio / curso", "Cursos solicitados");
    assertThat(renderer.html(data().toBuilder().coursesSelected(true).build()))
        .contains("Cursos solicitados", ">Curso</th>")
        .doesNotContain("Espacio / curso", "Espacios solicitados");
  }

  @Test
  void rendersInPrivateDirectoryWithMinimalEnvironmentAndCleansIt() throws Exception {
    final var binary =
        script(
            "for arg do case \"$arg\" in --print-to-pdf=*) output=${arg#*=};; esac; done\n"
                + "/usr/bin/env > '"
                + temporary.resolve("environment")
                + "'\n"
                + "/usr/bin/stat -c %a \"$(/usr/bin/dirname \"$output\")\" > '"
                + temporary.resolve("mode")
                + "'\n"
                + "printf '%%PDF-1.7\\n%%%%EOF\\n' > \"$output\"\n");

    final var bytes = renderer(binary.toString(), Duration.ofSeconds(2)).render(data());

    assertThat(new String(bytes, StandardCharsets.US_ASCII)).startsWith("%PDF-").contains("%%EOF");
    assertThat(Files.readString(temporary.resolve("mode")).strip()).isEqualTo("700");
    final var environment = Files.readString(temporary.resolve("environment"));
    assertThat(environment).contains("HOME=", "TMPDIR=", "LANG=C.UTF-8");
    assertThat(environment).doesNotContain("JWT", "DB_PASSWORD", "AWS_", "PATH=");
    assertNoRenderDirectories();
  }

  @Test
  void rejectsPartialOutputFromFailingProcessAndReleasesPermit() throws Exception {
    final var binary =
        script(
            "for arg do case \"$arg\" in --print-to-pdf=*) printf '%%PDF-1.7\\n%%%%EOF' > \"${arg#*=}\";; esac; done\nexit 1\n");
    final var renderer = renderer(binary.toString(), Duration.ofSeconds(1));

    assertThatThrownBy(() -> renderer.render(data()))
        .isInstanceOf(EnrollmentPdfUnavailableException.class);
    assertThatThrownBy(() -> renderer.render(data()))
        .isInstanceOf(EnrollmentPdfUnavailableException.class);
    assertNoRenderDirectories();
  }

  @Test
  void rejectsInvalidOutputEvenWhenProcessSucceeds() throws Exception {
    final var binary =
        script(
            "for arg do case \"$arg\" in --print-to-pdf=*) printf '<html>not a PDF</html>' > \"${arg#*=}\";; esac; done\n");

    assertThatThrownBy(() -> renderer(binary.toString(), Duration.ofSeconds(1)).render(data()))
        .isInstanceOf(EnrollmentPdfUnavailableException.class);
    assertNoRenderDirectories();
  }

  @Test
  void timeoutTerminatesChildProcessAndCleansTemporaryFiles() throws Exception {
    final var child = temporary.resolve("child-pid");
    final var binary = script("/bin/sleep 30 &\necho $! > '" + child + "'\nwait\n");
    final var renderer = renderer(binary.toString(), Duration.ofMillis(250));
    final long started = System.nanoTime();

    assertThatThrownBy(() -> renderer.render(data()))
        .isInstanceOf(EnrollmentPdfUnavailableException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    final long pid = Long.parseLong(Files.readString(child).strip());
    final var stat = Path.of("/proc", Long.toString(pid), "stat");
    assertThat(!Files.exists(stat) || Files.readString(stat).contains(") Z ")).isTrue();
    assertNoRenderDirectories();
  }

  @Test
  void rejectsConcurrentRenderWithoutQueueAndAllowsNextRender() throws Exception {
    final var started = temporary.resolve("started");
    final var binary =
        script(
            "echo started > '"
                + started
                + "'\n/bin/sleep 1\n"
                + "for arg do case \"$arg\" in --print-to-pdf=*) printf '%%PDF-1.7\\n%%%%EOF' > \"${arg#*=}\";; esac; done\n");
    final var renderer = renderer(binary.toString(), Duration.ofSeconds(3));
    final var first = CompletableFuture.supplyAsync(() -> renderer.render(data()));
    final long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (!Files.exists(started) && System.nanoTime() < waitUntil) {
      Thread.sleep(10);
    }
    assertThat(Files.exists(started)).isTrue();

    assertThatThrownBy(() -> renderer.render(data()))
        .isInstanceOfSatisfying(
            EnrollmentPdfUnavailableException.class,
            exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(5));
    assertThat(first.get(3, TimeUnit.SECONDS)).isNotEmpty();
    assertThat(renderer.render(data())).isNotEmpty();
    assertNoRenderDirectories();
  }

  private ChromiumEnrollmentPdfRenderer renderer(final String executable, final Duration timeout)
      throws Exception {
    final var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setCharacterEncoding("UTF-8");
    final var templates = new SpringTemplateEngine();
    templates.setTemplateResolver(resolver);
    return new ChromiumEnrollmentPdfRenderer(templates, executable, timeout, temporary);
  }

  private Path script(final String body) throws Exception {
    final var binary = temporary.resolve("fake-chrome");
    Files.writeString(binary, "#!/bin/sh\nset -eu\n" + body);
    assertThat(binary.toFile().setExecutable(true, true)).isTrue();
    return binary;
  }

  private void assertNoRenderDirectories() throws Exception {
    try (final var paths = Files.list(temporary)) {
      assertThat(paths.filter(path -> path.getFileName().toString().startsWith("pdf-")).toList())
          .isEmpty();
    }
  }

  private static EnrollmentPdfData data() {
    return EnrollmentPdfData.builder()
        .institution(
            new EnrollmentPdfData.InstitutionDetails(
                "Conservatorio Ñ", "Córdoba", "123", "contacto@example.test"))
        .generatedAt(Instant.parse("2026-11-05T12:00:00Z"))
        .settings(EnrollmentPdfSettingsResponse.defaults())
        .sections(
            List.of(
                new EnrollmentPdfData.Section(
                    "Preferencias",
                    List.of(new EnrollmentPdfData.Field("Autoriza imágenes", "No")))))
        .selections(
            List.of(
                new EnrollmentPdfData.Selection(
                    "Piano", "Nivel 1", "Piano", "Plan 2026", "Solicitada")))
        .hasResponsible(false)
        .build();
  }
}
