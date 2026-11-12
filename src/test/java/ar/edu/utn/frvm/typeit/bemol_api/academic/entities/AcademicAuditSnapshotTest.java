package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcademicAuditSnapshotTest {
  @Test
  void levelDescriptionIsComparedButNeverIncludedInDetails() {
    final var level = AcademicLevel.create(new StudyPlan(), "Nivel 1", 1, "Privado anterior");
    final var before = level.auditSnapshot();
    level.update("Nivel 1", 1, "Privado posterior");

    final var details = AuditChangeDetails.change("Nivel 1", before, level.auditSnapshot());

    assertThat(details.get("changedFields")).isEqualTo(List.of("description"));
    assertThat(details.toString()).doesNotContain("Privado anterior", "Privado posterior");
    assertThat(before.values()).containsEntry("description", "Privado anterior");
    assertThat(before.visibleValues()).doesNotContainKey("description");
  }

  @Test
  void scheduleOrderingAndAuxiliaryIdsDoNotProduceFalseChanges() {
    final var day = CourseClassDay.builder().dayOfWeek(CourseDay.MONDAY).capacity(12).build();
    final var early = schedule(day, "09:00", "10:00");
    final var late = schedule(day, "14:00", "15:00");
    final var before = day.auditSnapshot(List.of(late, early));
    final var after =
        day.auditSnapshot(
            List.of(schedule(day, "09:00", "10:00"), schedule(day, "14:00", "15:00")));

    assertThat(before.values()).isEqualTo(after.values());
    assertThat(before.visibleValues()).containsEntry("dayOfWeek", "Lunes");
    assertThat(before.labeledValues()).containsKey("fieldLabels");
  }

  @Test
  void teacherIdentityIsComparedEvenWhenNamesMatch() {
    final var first = teacher(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    final var second = teacher(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    final var courseClass = CourseClass.builder().classNumber(1).build();
    final var before = courseClass.auditSnapshot(List.of(first), List.of());
    final var after = courseClass.auditSnapshot(List.of(second), List.of());

    assertThat(before.values()).isNotEqualTo(after.values());
    assertThat(before.visibleValues()).isEqualTo(after.visibleValues());
    assertThat(before.visibleValues().toString())
        .contains("Ana Pérez")
        .doesNotContain(first.getPerson().getId().toString());
    assertThat(courseClass.auditSnapshot(List.of(first, second), List.of()).values())
        .isEqualTo(courseClass.auditSnapshot(List.of(second, first), List.of()).values());
  }

  private CourseClassSchedule schedule(
      final CourseClassDay day, final String start, final String end) {
    return CourseClassSchedule.builder()
        .id(UUID.randomUUID())
        .day(day)
        .startTime(LocalTime.parse(start))
        .endTime(LocalTime.parse(end))
        .build();
  }

  private CourseClassTeacher teacher(final UUID personId) {
    return CourseClassTeacher.builder()
        .id(UUID.randomUUID())
        .person(Person.builder().id(personId).firstName("Ana").lastName("Pérez").build())
        .build();
  }
}
