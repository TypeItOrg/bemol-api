package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentHistoryRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentHistoryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CreateManualCourseEnrollmentRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollApplicationCourseRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.UpdateAcademicEnrollmentStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.WithdrawCourseEnrollmentRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentService {
  private final CreateCourseEnrollmentUseCase creation;
  private final ChangeCourseEnrollmentStatusUseCase statusChanges;
  private final AcademicAccessGuard access;
  private final CourseEnrollmentRepository enrollments;
  private final CourseEnrollmentHistoryRepository history;
  private final CourseEnrollmentResponseFactory responses;

  @Transactional
  public CourseEnrollmentResponse createManual(
      final UUID institutionId,
      final CreateManualCourseEnrollmentRequest request,
      final @Nullable UUID authorityPersonId) {
    return creation.manual(institutionId, request, authorityPersonId);
  }

  @Transactional
  public CourseEnrollmentResponse enrollApplicationCourse(
      final UUID institutionId,
      final UUID applicationId,
      final UUID applicationCourseId,
      final EnrollApplicationCourseRequest request,
      final @Nullable UUID authorityPersonId) {
    return creation.fromApplication(
        institutionId, applicationId, applicationCourseId, request, authorityPersonId);
  }

  @Transactional
  public CourseEnrollmentResponse withdraw(
      final UUID institutionId,
      final UUID enrollmentId,
      final WithdrawCourseEnrollmentRequest request,
      final @Nullable UUID authorityPersonId) {
    return statusChanges.withdraw(institutionId, enrollmentId, request, authorityPersonId);
  }

  @Transactional
  public CourseEnrollmentResponse updateAcademicStatus(
      final UUID institutionId,
      final UUID enrollmentId,
      final UpdateAcademicEnrollmentStatusRequest request,
      final @Nullable UUID authorityPersonId) {
    return statusChanges.academicStatus(institutionId, enrollmentId, request, authorityPersonId);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<CourseEnrollmentResponse> listInstitutional(
      final UUID institutionId,
      final @Nullable CourseEnrollmentStatus status,
      final @Nullable AcademicEnrollmentStatus academicStatus,
      final Pageable pageable) {
    return responses.from(
        enrollments.findByInstitution_Id(institutionId, status, academicStatus, pageable));
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<CourseEnrollmentResponse> listOwn(
      final UUID institutionId,
      final UUID personId,
      final @Nullable CourseEnrollmentStatus status,
      final @Nullable AcademicEnrollmentStatus academicStatus,
      final Pageable pageable) {
    return responses.from(
        enrollments.findByInstitutionIdAndStudentPersonId(
            institutionId, personId, status, academicStatus, pageable));
  }

  @Transactional(readOnly = true)
  public CourseEnrollmentResponse get(final UUID institutionId, final UUID enrollmentId) {
    access.require(
        PermissionCode.COURSE_ENROLLMENT_READ,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    final var enrollment =
        enrollments
            .findByIdAndInstitutionId(enrollmentId, institutionId)
            .orElseThrow(
                () ->
                    new EnrollmentValidationException(
                        EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND));

    return responses.from(enrollment);
  }

  @Transactional(readOnly = true)
  public List<CourseEnrollmentHistoryResponse> history(
      final UUID institutionId, final UUID enrollmentId) {
    access.require(
        PermissionCode.COURSE_ENROLLMENT_READ,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);
    enrollments
        .findByIdAndInstitutionId(enrollmentId, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentValidationException(EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND));

    return history.findByCourseEnrollment_IdOrderByChangedAtDesc(enrollmentId).stream()
        .map(CourseEnrollmentHistoryResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<CourseEnrollmentResponse> listForTeacherClass(
      final UUID institutionId, final UUID personId, final UUID classId, final Pageable pageable) {
    return responses.from(
        enrollments.findForTeacherClass(institutionId, personId, classId, pageable));
  }
}
