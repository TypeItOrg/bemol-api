package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.SecurityErrorMessages;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeAccess {

  private final PersonRoleAssignmentRepository roleAssignments;
  private final CourseClassTeacherRepository courseClassTeacherRepository;
  private final CourseEnrollmentGradeStore store;

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public void requireTeacherForClass(
      final UUID institutionId, final UUID personId, final UUID classId) {
    if (!roleAssignments.existsByPerson_IdAndInstitution_IdAndRole_Code(
        personId, institutionId, SystemRoleCode.TEACHER.name())) {
      throw new AccessDeniedException(SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE);
    }

    if (!courseClassTeacherRepository.existsByInstitution_IdAndPerson_IdAndCourseClass_Id(
        institutionId, personId, classId)) {
      throw new EnrollmentValidationException(GradeMessages.TEACHER_NOT_ASSIGNED);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public void requireEnrollmentInClass(
      final UUID institutionId, final UUID classId, final UUID enrollmentId) {
    final CourseEnrollment enrollment = store.findEnrollment(institutionId, enrollmentId);

    if (!enrollment.getCourseClass().getId().equals(classId)) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_CLASS_MISMATCH);
    }
  }
}
