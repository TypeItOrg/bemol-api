package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "course_classes")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class CourseClass extends Auditable implements AuditSubject {

  @Id
  @GeneratedUUIDv7
  @Column(name = "course_class_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Column(name = "class_number", nullable = false, updatable = false)
  private Integer classNumber;

  public static CourseClass create(
      final Institution institution, final Course course, final int classNumber) {
    return CourseClass.builder()
        .institution(institution)
        .course(course)
        .classNumber(classNumber)
        .build();
  }

  public String displayName() {
    return "Clase " + classNumber;
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields().expose("classNumber", "Clase", classNumber).build();
  }

  public AuditSnapshot.Snapshot auditSnapshot(
      final List<CourseClassTeacher> teachers, final List<AuditSnapshot.Snapshot> days) {
    final var teacherStates =
        teachers.stream()
            .sorted(Comparator.comparing(value -> value.getPerson().getId()))
            .map(entry -> entry.auditSnapshot())
            .toList();
    return AuditSnapshot.fields()
        .expose("classNumber", "Clase", classNumber)
        .expose(
            "teachers",
            "Docentes",
            teacherStates.stream().map(entry -> entry.values()).toList(),
            teacherStates.stream().map(entry -> entry.labeledValues()).toList())
        .expose(
            "days",
            "Días",
            days.stream().map(entry -> entry.values()).toList(),
            days.stream().map(entry -> entry.labeledValues()).toList())
        .build();
  }
}
