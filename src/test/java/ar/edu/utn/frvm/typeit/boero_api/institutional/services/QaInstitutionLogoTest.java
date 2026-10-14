package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionPublicAccessResponse.logoUrl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.boero_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionLogoTooLargeException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InvalidInstitutionLogoException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class QaInstitutionLogoTest {
  private InstitutionRepository institutions;
  private StorageService storage;
  private InstitutionLogoUseCase logos;
  private Institution institution;

  @BeforeEach
  void setup() {
    institutions = mock(InstitutionRepository.class);
    storage = mock(StorageService.class);
    logos = new InstitutionLogoUseCase(institutions, storage);
    institution = QaInstitutionPublicAccessTest.institution();
    institution.replaceLogo("old-key", "image/png", "old-version", 10);
    lenient()
        .when(institutions.findByIdForUpdate(institution.getId()))
        .thenReturn(Optional.of(institution));
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void clear() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  public static byte[] image(final String type, final int width, final int height)
      throws IOException {
    final var output = new ByteArrayOutputStream();
    final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ImageIO.write(image, type, output);
    image.flush();
    return output.toByteArray();
  }

  static MockMultipartFile png() throws IOException {
    return new MockMultipartFile("file", "../../untrusted.png", "image/png", image("png", 2, 2));
  }

  static void finish(final int status) {
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(sync -> sync.afterCompletion(status));
  }

  @Test
  void L01_platformLogoLifecycle_replacementAfterCommit() throws IOException {
    final var response = logos.replace(institution.getId(), png());
    assertThat(response.logoUrl())
        .startsWith("/api/v1/institutions/" + institution.getId() + "/logo?v=")
        .doesNotContain("old-version", "old-key", "untrusted");
    verify(storage, never()).deletePhysicalFile(anyString());
    finish(TransactionSynchronization.STATUS_COMMITTED);
    verify(storage).deletePhysicalFile("old-key");
    final var currentKey = institution.getLogoKey();
    assertThat(currentKey).isNotNull();
    verify(storage, never()).deletePhysicalFile(currentKey);
  }

  @Test
  void L03_replacementFailure_storageFailurePreservesOldAndCleansNew() throws IOException {
    doThrow(new IllegalStateException("storage offline"))
        .when(storage)
        .write(anyString(), anyString(), anyLong(), any());
    assertThatThrownBy(() -> logos.replace(institution.getId(), png()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(institution.getLogoKey()).isEqualTo("old-key");
    verify(storage, never()).deletePhysicalFile(anyString());
    finish(TransactionSynchronization.STATUS_ROLLED_BACK);
    verify(storage)
        .deletePhysicalFile(
            argThat(key -> key.startsWith("institutions/" + institution.getId() + "/logos/")));
    verify(storage, never()).deletePhysicalFile("old-key");
  }

  @Test
  void L01_platformLogoLifecycle_deleteOnlyAfterCommit() {
    logos.delete(institution.getId());
    assertThat(logoUrl(institution)).isNull();
    verify(storage, never()).deletePhysicalFile(anyString());
    finish(TransactionSynchronization.STATUS_COMMITTED);
    verify(storage).deletePhysicalFile("old-key");
  }

  @Test
  void L03_realImageValidation_pngJpeg() throws IOException {
    assertThat(InstitutionLogoPolicy.prepare(png()).contentType()).isEqualTo("image/png");
    assertThat(
            InstitutionLogoPolicy.prepare(
                    new MockMultipartFile("file", "photo.jpeg", "image/jpeg", image("jpeg", 3, 3)))
                .contentType())
        .isEqualTo("image/jpeg");
  }

  @Test
  void L03_realImageValidation_spoofedTruncatedSvgEmptyAndExcessiveDimensions() throws IOException {
    final var png = image("png", 2, 2);
    for (final var invalid :
        new MockMultipartFile[] {
          new MockMultipartFile("file", "logo.png", "image/png", "<html>attack</html>".getBytes()),
          new MockMultipartFile("file", "logo.png", "image/png", "<svg/>".getBytes()),
          new MockMultipartFile("file", "logo.jpg", "image/jpeg", png),
          new MockMultipartFile(
              "file", "logo.png", "image/png", Arrays.copyOf(png, png.length - 8)),
          new MockMultipartFile("file", "logo.png", "image/png", new byte[0]),
          new MockMultipartFile("file", "logo.png", "image/png", image("png", 2100, 2000))
        }) {
      assertThatThrownBy(() -> InstitutionLogoPolicy.prepare(invalid))
          .isInstanceOf(InvalidInstitutionLogoException.class);
    }
  }

  @Test
  void L03_realImageValidation_overByteLimit() {
    final var oversized =
        new MockMultipartFile(
            "file", "logo.png", "image/png", new byte[(int) InstitutionLogoPolicy.MAX_BYTES + 1]);

    assertThatThrownBy(() -> InstitutionLogoPolicy.prepare(oversized))
        .isInstanceOf(InstitutionLogoTooLargeException.class);
  }
}
