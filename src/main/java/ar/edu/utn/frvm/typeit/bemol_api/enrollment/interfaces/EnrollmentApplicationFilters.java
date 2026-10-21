package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

final class EnrollmentApplicationFilters {
  static final String PENDING_COURSES_FILTER =
      """
      AND (:pendingCourses IS NULL OR (
        application.status IN (
          ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus.APPROVED,
          ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus.PROVISIONALLY_APPROVED)
        AND EXISTS (SELECT selection.id FROM EnrollmentApplicationCourse selection
          WHERE selection.enrollmentApplication.id = application.id)
        AND ((:pendingCourses = true AND EXISTS (
          SELECT pending.id FROM EnrollmentApplicationCourse pending
          WHERE pending.enrollmentApplication.id = application.id AND pending.status IN (
            ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus.PENDING,
            ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus.WAITLISTED)))
          OR (:pendingCourses = false AND NOT EXISTS (
          SELECT pending.id FROM EnrollmentApplicationCourse pending
          WHERE pending.enrollmentApplication.id = application.id AND pending.status IN (
            ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus.PENDING,
            ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus.WAITLISTED))))))
      """;

  private EnrollmentApplicationFilters() {}
}
