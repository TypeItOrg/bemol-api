package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.InvalidStorageKeyException;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.LocalStorageService;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageFileNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidFileException;
import ar.edu.utn.frvm.typeit.bemol_api.support.EnrollmentDocumentTestData;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

class LocalStorageServiceTest {

  @TempDir Path tempDir;

  private LocalStorageService storageService;
  private final UUID applicationId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    storageService = new LocalStorageService(tempDir.toString());
  }

  @Test
  @DisplayName("Should successfully store a valid PDF file")
  void store_validPdf() throws Exception {
    MockMultipartFile file = EnrollmentDocumentTestData.pdf("document.pdf");
    byte[] content = file.getBytes();

    var stored = EnrollmentFilePolicy.prepare(applicationId, file);
    storageService.write(stored.storagePath(), stored.contentType(), stored.size(), file);

    assertThat(stored).isNotNull();
    assertThat(stored.safeFileName()).endsWith(".pdf");
    assertThat(stored.contentType()).isEqualTo("application/pdf");
    assertThat(stored.size()).isEqualTo(content.length);

    Resource resource = storageService.loadAsResource(stored.storagePath());
    assertThat(resource.exists()).isTrue();
    assertThat(resource.getContentAsByteArray()).isEqualTo(content);
  }

  @Test
  @DisplayName("Should successfully store a valid PNG image")
  void store_validPng() throws Exception {
    MockMultipartFile file = EnrollmentDocumentTestData.png("photo.png");

    var stored = EnrollmentFilePolicy.prepare(applicationId, file);
    storageService.write(stored.storagePath(), stored.contentType(), stored.size(), file);

    assertThat(stored.safeFileName()).endsWith(".png");
    assertThat(stored.contentType()).isEqualTo("image/png");
  }

  @Test
  @DisplayName("Should reject empty file")
  void store_emptyFile() {
    MockMultipartFile file =
        new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

    assertThatThrownBy(() -> EnrollmentFilePolicy.prepare(applicationId, file))
        .isInstanceOf(InvalidFileException.class)
        .hasMessageContaining("no puede estar vacío");
  }

  @Test
  @DisplayName("Should reject file exceeding max size")
  void store_fileTooLarge() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "large.pdf",
            "application/pdf",
            new byte[(int) (EnrollmentFilePolicy.MAX_FILE_SIZE_BYTES + 1)]);

    assertThatThrownBy(() -> EnrollmentFilePolicy.prepare(applicationId, file))
        .isInstanceOf(InvalidFileException.class)
        .hasMessageContaining("no puede superar los 10MB");
  }

  @Test
  @DisplayName("Should reject unsupported mime type")
  void store_unsupportedMimeType() {
    MockMultipartFile file =
        new MockMultipartFile("file", "script.sh", "application/x-sh", "echo hi".getBytes());

    assertThatThrownBy(() -> EnrollmentFilePolicy.prepare(applicationId, file))
        .isInstanceOf(InvalidFileException.class)
        .hasMessage(EnrollmentMessages.FILE_CONTENT_INVALID);
  }

  @Test
  @DisplayName("Should reject path traversal attempts when loading resource")
  void loadAsResource_pathTraversal() {
    assertThatThrownBy(() -> storageService.loadAsResource("../../../etc/passwd"))
        .isInstanceOf(InvalidStorageKeyException.class);
  }

  @Test
  @DisplayName("Should throw StorageFileNotFoundException for non-existent file")
  void loadAsResource_notFound() {
    assertThatThrownBy(() -> storageService.loadAsResource(applicationId + "/missing.pdf"))
        .isInstanceOf(StorageFileNotFoundException.class);
  }

  @Test
  @DisplayName("Should read stored file as InputStream")
  void loadAsInputStream_success() throws Exception {
    MockMultipartFile file = EnrollmentDocumentTestData.png("sample.png");
    byte[] content = file.getBytes();

    var stored = EnrollmentFilePolicy.prepare(applicationId, file);
    storageService.write(stored.storagePath(), stored.contentType(), stored.size(), file);

    try (InputStream is = storageService.loadAsInputStream(stored.storagePath())) {
      byte[] readBytes = is.readAllBytes();
      assertThat(readBytes).isEqualTo(content);
    }
  }

  @Test
  @DisplayName("Should delete physical file from storage")
  void deletePhysicalFile_success() throws Exception {
    MockMultipartFile file = EnrollmentDocumentTestData.pdf("doc.pdf");

    var stored = EnrollmentFilePolicy.prepare(applicationId, file);
    storageService.write(stored.storagePath(), stored.contentType(), stored.size(), file);
    Path filePath = tempDir.resolve(stored.storagePath());
    assertThat(Files.exists(filePath)).isTrue();

    boolean deleted = storageService.deletePhysicalFile(stored.storagePath());
    assertThat(deleted).isTrue();
    assertThat(Files.exists(filePath)).isFalse();
  }
}
