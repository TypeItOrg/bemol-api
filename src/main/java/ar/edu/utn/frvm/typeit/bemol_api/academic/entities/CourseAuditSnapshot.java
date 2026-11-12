package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import java.util.List;
import java.util.Map;

final class CourseAuditSnapshot {
  private CourseAuditSnapshot() {}

  static String name(final Course course) {
    return course.getAcademicSpace().getName()
        + " — "
        + course.getStudyPlan().getName()
        + " ("
        + course.getAcademicYear().getYear()
        + ")";
  }

  static AuditSnapshot.Snapshot of(final Course course) {
    return AuditSnapshot.fields()
        .expose(
            "status",
            "Estado",
            course.getStatus().name(),
            Map.of("code", course.getStatus().name(), "label", course.getStatus().getLabel()))
        .build();
  }

  static AuditSnapshot.Snapshot of(
      final Course course,
      final List<AuditSnapshot.Snapshot> classes,
      final int teacherCount,
      final int dayCount,
      final int scheduleCount) {
    return AuditSnapshot.fields()
        .expose(
            "status",
            "Estado",
            course.getStatus().name(),
            Map.of("code", course.getStatus().name(), "label", course.getStatus().getLabel()))
        .expose("classCount", "Clases", classes.size())
        .expose("teacherCount", "Docentes", teacherCount)
        .expose("dayCount", "Días de clase", dayCount)
        .expose("scheduleCount", "Horarios", scheduleCount)
        .expose(
            "classes",
            "Clases del curso",
            classes.stream().map(entry -> entry.values()).toList(),
            classes.stream().map(entry -> entry.labeledValues()).toList())
        .build();
  }
}
