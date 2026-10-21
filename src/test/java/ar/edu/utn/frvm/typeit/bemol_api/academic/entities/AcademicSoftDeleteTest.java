package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcademicSoftDeleteTest {

  private final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
  private final Instant deletedAt = LocalDateTime.of(2026, 8, 16, 12, 0).toInstant(ZoneOffset.UTC);

  @Test
  void preservesOperationalStateAcrossDeleteAndRestore() {
    final var year =
        AcademicYear.create(
            institution,
            2027,
            LocalDate.of(2027, 3, 1),
            LocalDate.of(2027, 12, 15),
            LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")));
    final var path = TrainingPath.create(institution, "Tecnicatura", null);
    final var plan = StudyPlan.create(institution, path, "Plan 2027", null, null);
    final var space =
        AcademicSpace.create(
            institution,
            "Programación",
            null,
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.INDIVIDUAL);
    final var instrument = Instrument.create(institution, "Examen", null);
    final var shift = Shift.create(institution, "Turno mañana", null);

    path.updateStatus(false);
    space.updateStatus(false);
    instrument.updateStatus(false);
    shift.updateStatus(false);

    assertThat(year.delete(deletedAt)).isTrue();
    assertThat(path.delete(deletedAt)).isTrue();
    assertThat(plan.delete(deletedAt)).isTrue();
    assertThat(space.delete(deletedAt)).isTrue();
    assertThat(instrument.delete(deletedAt)).isTrue();
    assertThat(shift.delete(deletedAt)).isTrue();

    assertThat(year.restore()).isTrue();
    assertThat(path.restore()).isTrue();
    assertThat(plan.restore()).isTrue();
    assertThat(space.restore()).isTrue();
    assertThat(instrument.restore()).isTrue();
    assertThat(shift.restore()).isTrue();
    assertThat(path.isActive()).isFalse();
    assertThat(space.isActive()).isFalse();
    assertThat(instrument.isActive()).isFalse();
    assertThat(shift.isActive()).isFalse();
  }

  @Test
  void rejectsDeleteWhenOperationalStateDoesNotAllowIt() {
    final var path = TrainingPath.create(institution, "Tecnicatura", null);
    final var space =
        AcademicSpace.create(
            institution,
            "Programación",
            null,
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.INDIVIDUAL);
    final var instrument = Instrument.create(institution, "Examen", null);
    final var shift = Shift.create(institution, "Turno mañana", null);

    assertThatThrownBy(() -> path.delete(deletedAt))
        .isInstanceOf(InvalidAcademicStateException.class);
    assertThatThrownBy(() -> space.delete(deletedAt))
        .isInstanceOf(InvalidAcademicStateException.class);
    assertThatThrownBy(() -> instrument.delete(deletedAt))
        .isInstanceOf(InvalidAcademicStateException.class);
    assertThatThrownBy(() -> shift.delete(deletedAt))
        .isInstanceOf(InvalidAcademicStateException.class);
  }
}
