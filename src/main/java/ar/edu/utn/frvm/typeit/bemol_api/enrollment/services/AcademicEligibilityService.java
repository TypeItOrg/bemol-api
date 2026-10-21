package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementStage;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.AcademicEligibilityResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.AcademicRequirementResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcademicEligibilityService {
  private final PrerequisiteRepository prerequisiteRepository;
  private final CourseEnrollmentRepository enrollmentRepository;

  @Transactional(readOnly = true)
  public Map<UUID, AcademicEligibilityResponse> evaluateCourses(
      final UUID institutionId, final UUID personId, final List<Course> courses) {
    if (courses.isEmpty()) {
      return Map.of();
    }
    final var targetIds =
        courses.stream()
            .filter(course -> course.getStudyPlanSpace() != null)
            .map(course -> course.getStudyPlanSpace().getId())
            .distinct()
            .toList();
    final var requirements =
        targetIds.isEmpty()
            ? List.<Prerequisite>of()
            : prerequisiteRepository.findByTargetStudyPlanSpace_IdIn(targetIds).stream()
                .filter(
                    requirement -> requirement.getRequirementStage() == RequirementStage.TO_ENROLL)
                .toList();
    final var requiredIds =
        requirements.stream()
            .map(requirement -> requirement.getRequiredStudyPlanSpace().getId())
            .distinct()
            .toList();
    final var evidence =
        requiredIds.isEmpty()
            ? Map.<UUID, List<AcademicEnrollmentStatus>>of()
            : enrollmentRepository
                .findAcademicEvidence(institutionId, personId, requiredIds)
                .stream()
                .collect(
                    Collectors.groupingBy(
                        enrollment -> enrollment.getCourse().getStudyPlanSpace().getId(),
                        Collectors.mapping(
                            enrollment -> enrollment.getAcademicStatus(), Collectors.toList())));
    final var result = new HashMap<UUID, AcademicEligibilityResponse>();
    for (final var course : courses) {
      final var evaluated =
          requirements.stream()
              .filter(
                  requirement ->
                      course.getStudyPlanSpace() != null
                          && requirement
                              .getTargetStudyPlanSpace()
                              .getId()
                              .equals(course.getStudyPlanSpace().getId()))
              .map(
                  requirement -> {
                    final var statuses =
                        evidence.getOrDefault(
                            requirement.getRequiredStudyPlanSpace().getId(), List.of());
                    final boolean satisfied =
                        statuses.stream()
                            .anyMatch(
                                status ->
                                    status == AcademicEnrollmentStatus.PASSED
                                        || status == AcademicEnrollmentStatus.PROMOTED
                                        || requirement.getRequiredCondition()
                                                == RequiredCondition.REGULAR
                                            && status == AcademicEnrollmentStatus.REGULARIZED);
                    return new AcademicRequirementResponse(
                        requirement.getId(),
                        requirement.getRequiredStudyPlanSpace().getId(),
                        requirement.getRequiredStudyPlanSpace().getAcademicSpace().getName(),
                        requirement.getRequiredCondition(),
                        statuses.stream().distinct().toList(),
                        satisfied);
                  })
              .toList();
      result.put(
          course.getId(),
          new AcademicEligibilityResponse(
              evaluated.stream()
                  .allMatch(
                      mappedAcademicRequirementResponse ->
                          mappedAcademicRequirementResponse.satisfied()),
              evaluated));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public AcademicEligibilityResponse evaluate(
      final UUID institutionId, final UUID personId, final Course course) {
    return requireNonNull(
        evaluateCourses(institutionId, personId, List.of(course)).get(course.getId()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireEligible(final UUID institutionId, final UUID personId, final Course course) {
    if (!evaluate(institutionId, personId, course).eligible()) {
      throw new EnrollmentValidationException(EnrollmentMessages.ACADEMIC_REQUIREMENTS_PENDING);
    }
  }
}
