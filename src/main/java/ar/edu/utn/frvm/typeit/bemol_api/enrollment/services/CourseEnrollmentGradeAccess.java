package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilitiesService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeAccess {

  private final InstitutionalCapabilitiesService capabilities;
  private final CourseClassTeacherRepository courseClassTeacherRepository;
  private final CourseEnrollmentGradeSupport support;

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public void requireTeacherReadForClass(
      final UUID institutionId, final UUID personId, final UUID classId) {
    capabilities.requireTeachingRead(institutionId, personId);
    requireAssignedTeacher(institutionId, personId, classId);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public void requireTeacherManageForClass(
      final UUID institutionId, final UUID personId, final UUID classId) {
    capabilities.requireTeachingGradeManage(institutionId, personId);
    requireAssignedTeacher(institutionId, personId, classId);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public void requireEnrollmentInClass(
      final UUID institutionId, final UUID classId, final UUID enrollmentId) {
    final CourseEnrollment enrollment = support.findEnrollment(institutionId, enrollmentId);

    if (!enrollment.getCourseClass().getId().equals(classId)) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_CLASS_MISMATCH);
    }
  }

  private void requireAssignedTeacher(
      final UUID institutionId, final UUID personId, final UUID classId) {
    if (!courseClassTeacherRepository.existsByInstitution_IdAndPerson_IdAndCourseClass_Id(
        institutionId, personId, classId)) {
      throw new AccessDeniedException(SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE);
    }
  }
}
