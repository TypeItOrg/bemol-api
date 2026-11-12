package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class EnrollmentFormBaseDefinition {
  private EnrollmentFormBaseDefinition() {}

  public static EnrollmentFormDefinition create() {
    return new EnrollmentFormDefinition(
        List.of(
            section(EnrollmentFormNativeBlock.PERSONAL_DATA, "Datos personales"),
            section(EnrollmentFormNativeBlock.SCHOOLING, "Escolaridad"),
            section(EnrollmentFormNativeBlock.HEALTH_INCLUSION, "Salud e inclusión"),
            section(EnrollmentFormNativeBlock.RESPONSIBLE, "Responsable"),
            section(EnrollmentFormNativeBlock.ACADEMIC_SELECTION, "Cursos"),
            section(EnrollmentFormNativeBlock.PREFERENCES, "Preferencias"),
            section(EnrollmentFormNativeBlock.DOCUMENTATION, "Documentación")),
        List.of());
  }

  private static Section section(final EnrollmentFormNativeBlock block, final String title) {
    return new Section(
        UUID.nameUUIDFromBytes(
            ("bemol.enrollment.BASE_V1." + block.name()).getBytes(StandardCharsets.UTF_8)),
        title,
        block,
        List.of());
  }
}
