package ar.edu.utn.frvm.typeit.bemol_api.enrollment.pdf.render;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentPdfRenderer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
@Slf4j
public class ChromiumEnrollmentPdfRenderer implements EnrollmentPdfRenderer {
  private static final int MAX_PDF_BYTES = 16 * 1024 * 1024;
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"))
          .withZone(ZoneId.of("America/Argentina/Cordoba"));

  private final SpringTemplateEngine templates;
  private final String executable;
  private final Duration timeout;
  private final Path temporaryRoot;
  private final String fontCss;
  private final Semaphore permit = new Semaphore(1);

  @Autowired
  public ChromiumEnrollmentPdfRenderer(
      final SpringTemplateEngine templates,
      @Value("${bemol.enrollment.pdf.chromium-executable:/opt/chrome/chrome}")
          final String executable)
      throws IOException {
    this(
        templates,
        executable,
        Duration.ofSeconds(10),
        Path.of(System.getProperty("java.io.tmpdir")));
  }

  ChromiumEnrollmentPdfRenderer(
      final SpringTemplateEngine templates,
      final String executable,
      final Duration timeout,
      final Path temporaryRoot)
      throws IOException {
    this.templates = templates;
    this.executable = executable;
    this.timeout = timeout;
    this.temporaryRoot = temporaryRoot;
    try (final var font = new ClassPathResource("pdf/manrope-latin.woff2").getInputStream()) {
      fontCss =
          "@font-face{font-family:Manrope;src:url('data:font/woff2;base64,"
              + Base64.getEncoder().encodeToString(font.readAllBytes())
              + "') format('woff2');font-weight:200 800;font-style:normal;font-display:block;}";
    }
  }

  @Override
  public byte[] render(final EnrollmentPdfData data) {
    if (!permit.tryAcquire()) {
      throw new EnrollmentPdfUnavailableException();
    }

    Path directory = null;
    Process process = null;
    try {
      // Chromium's temporary Unix-socket paths must stay short.
      directory =
          Files.createTempDirectory(
              temporaryRoot,
              "pdf-",
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      final var html = directory.resolve("application.html");
      final var output = directory.resolve("application.pdf");
      Files.writeString(html, html(data), StandardCharsets.UTF_8);

      final var builder =
          new ProcessBuilder(command(directory, html, output))
              .directory(directory.toFile())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD);
      builder.environment().clear();
      builder.environment().put("LANG", "C.UTF-8");
      builder.environment().put("LC_ALL", "C.UTF-8");
      builder.environment().put("HOME", directory.toString());
      builder.environment().put("TMPDIR", directory.toString());
      process = builder.start();

      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new EnrollmentPdfUnavailableException();
      }
      if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
        log.debug("[EnrollmentPdf] Renderer exited, exitCode: {}", process.exitValue());
        throw new EnrollmentPdfUnavailableException();
      }

      final long size = Files.size(output);
      if (size < 8 || size > MAX_PDF_BYTES) {
        throw new EnrollmentPdfUnavailableException();
      }
      final var bytes = Files.readAllBytes(output);
      if (!new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
          || !new String(
                  bytes,
                  Math.max(0, bytes.length - 1024),
                  Math.min(1024, bytes.length),
                  StandardCharsets.US_ASCII)
              .contains("%%EOF")) {
        throw new EnrollmentPdfUnavailableException();
      }
      return bytes;
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new EnrollmentPdfUnavailableException();
    } catch (final IOException exception) {
      throw new EnrollmentPdfUnavailableException();
    } finally {
      try {
        terminate(process);
        deleteDirectory(directory);
      } finally {
        permit.release();
      }
    }
  }

  String html(final EnrollmentPdfData data) {
    final var context = new Context(Locale.forLanguageTag("es-AR"));
    context.setVariable("data", data);
    context.setVariable("fontCss", fontCss);
    context.setVariable("generatedAt", DATE_FORMAT.format(data.generatedAt()));
    context.setVariable(
        "submittedAt", data.submittedAt() == null ? null : DATE_FORMAT.format(data.submittedAt()));
    return templates.process("pdf/enrollment-application", context);
  }

  private List<String> command(final Path directory, final Path html, final Path output) {
    return List.of(
        "/usr/bin/setsid",
        "--wait",
        executable,
        "--headless",
        "--disable-background-networking",
        "--disable-component-update",
        "--disable-default-apps",
        "--disable-sync",
        "--disable-crash-reporter",
        "--disable-breakpad",
        "--disable-dev-shm-usage",
        "--no-first-run",
        "--no-default-browser-check",
        "--user-data-dir=" + directory.resolve("profile"),
        "--disk-cache-dir=" + directory.resolve("cache"),
        "--print-to-pdf=" + output,
        "--no-pdf-header-footer",
        html.toUri().toASCIIString());
  }

  private static void terminate(final Process process) {
    if (process == null) {
      return;
    }
    final boolean interrupted = Thread.interrupted();
    final var descendants = new ArrayList<>(process.descendants().toList());
    try {
      // The private process group also contains Chromium helpers that reparent themselves.
      final var kill =
          new ProcessBuilder("/bin/kill", "-KILL", "--", "-" + process.pid())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      kill.waitFor(500, TimeUnit.MILLISECONDS);
      kill.destroyForcibly();
    } catch (final IOException exception) {
      log.debug("[EnrollmentPdf] Process-group cleanup unavailable");
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
    descendants.reversed().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
    try {
      process.waitFor(500, TimeUnit.MILLISECONDS);
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void deleteDirectory(final Path directory) {
    if (directory == null) {
      return;
    }
    try (final var paths = Files.walk(directory)) {
      for (final var path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
