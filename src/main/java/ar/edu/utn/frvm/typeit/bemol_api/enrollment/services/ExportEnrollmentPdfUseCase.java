package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.InstitutionLogoUseCase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExportEnrollmentPdfUseCase {
  private final ExtractEnrollmentPdfDataUseCase data;
  private final InstitutionLogoUseCase logos;
  private final EnrollmentPdfRenderer renderer;

  public byte[] export(
      final UUID institutionId, final UUID applicationId, final @Nullable UUID subjectPersonId) {
    return render(institutionId, data.extract(institutionId, applicationId, subjectPersonId));
  }

  public byte[] preview(final UUID institutionId, final EnrollmentPdfSettingsRequest input) {
    return render(institutionId, data.example(institutionId, input));
  }

  private byte[] render(final UUID institutionId, final EnrollmentPdfData snapshot) {
    final var logo = logos.getForExport(institutionId);
    if (logo.isEmpty()) {
      return renderer.render(snapshot);
    }
    try {
      final var image = logo.get();
      final var uri =
          "data:"
              + image.contentType()
              + ";base64,"
              + Base64.getEncoder().encodeToString(image.resource().getContentAsByteArray());
      return renderer.render(snapshot.toBuilder().logoDataUri(uri).build());
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
