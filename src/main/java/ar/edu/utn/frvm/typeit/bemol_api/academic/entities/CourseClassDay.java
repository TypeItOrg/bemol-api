package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "course_class_days")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class CourseClassDay extends Auditable implements AuditSubject {

  @Id
  @GeneratedUUIDv7
  @Column(name = "course_class_day_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_class_id", nullable = false)
  private CourseClass courseClass;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false, length = 20)
  private CourseDay dayOfWeek;

  @Column private @Nullable Integer capacity;

  @Column(name = "period_duration_minutes")
  private @Nullable Integer periodDurationMinutes;

  public static CourseClassDay create(
      final Institution institution,
      final CourseClass courseClass,
      final CourseDay dayOfWeek,
      final @Nullable Integer capacity,
      final @Nullable Integer periodDurationMinutes) {
    return CourseClassDay.builder()
        .institution(institution)
        .courseClass(courseClass)
        .dayOfWeek(dayOfWeek)
        .capacity(capacity)
        .periodDurationMinutes(periodDurationMinutes)
        .build();
  }

  private AuditSnapshot.Builder auditFields() {
    final var dayName =
        switch (dayOfWeek) {
          case MONDAY -> "Lunes";
          case TUESDAY -> "Martes";
          case WEDNESDAY -> "Miércoles";
          case THURSDAY -> "Jueves";
          case FRIDAY -> "Viernes";
        };
    return AuditSnapshot.fields()
        .expose("dayOfWeek", "Día", dayOfWeek.name(), dayName)
        .expose("capacity", "Cupo", capacity)
        .expose("periodDurationMinutes", "Duración del turno (minutos)", periodDurationMinutes);
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return auditFields().build();
  }

  public AuditSnapshot.Snapshot auditSnapshot(final List<CourseClassSchedule> schedules) {
    final var states =
        schedules.stream()
            .sorted(
                Comparator.comparing((CourseClassSchedule entry) -> entry.getStartTime())
                    .thenComparing((CourseClassSchedule entry) -> entry.getEndTime()))
            .map(entry -> entry.auditSnapshot())
            .toList();
    return auditFields()
        .expose(
            "schedules",
            "Horarios",
            states.stream().map(entry -> entry.values()).toList(),
            states.stream().map(entry -> entry.labeledValues()).toList())
        .build();
  }
}
