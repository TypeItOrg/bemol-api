package ar.edu.utn.frvm.typeit.boero_api.authorization.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScopedResource {
  TRAINING_PATH("TrainingPath", "resource.id"),
  STUDY_PLAN("StudyPlan", "resource.trainingPath.id"),
  ACADEMIC_LEVEL("AcademicLevel", "resource.studyPlan.trainingPath.id"),
  STUDY_PLAN_SPACE("StudyPlanSpace", "resource.studyPlan.trainingPath.id"),
  PREREQUISITE("Prerequisite", "resource.targetStudyPlanSpace.studyPlan.trainingPath.id"),
  COURSE("Course", "resource.studyPlanSpace.studyPlan.trainingPath.id"),
  COURSE_CLASS("CourseClass", "resource.course.studyPlanSpace.studyPlan.trainingPath.id"),
  ENROLLMENT_APPLICATION("EnrollmentApplication", "resource.trainingPathId"),
  ENROLLMENT_APPLICATION_COURSE(
      "EnrollmentApplicationCourse", "resource.enrollmentApplication.trainingPathId"),
  COURSE_ENROLLMENT("CourseEnrollment", "resource.course.studyPlanSpace.studyPlan.trainingPath.id"),
  COURSE_ENROLLMENT_GRADE(
      "CourseEnrollmentGrade",
      "resource.courseEnrollment.course.studyPlanSpace.studyPlan.trainingPath.id");
  private final String entity;
  private final String trainingPathExpression;
}
