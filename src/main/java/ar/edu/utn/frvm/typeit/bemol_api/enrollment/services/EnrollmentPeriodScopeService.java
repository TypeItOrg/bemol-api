package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationCourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPeriodRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodOfferingRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentPeriodScopeService {
  private final StudyPlanRepository studyPlanRepository;
  private final AcademicLevelRepository academicLevelRepository;
  private final EnrollmentApplicationRepository applicationRepository;
  private final EnrollmentPeriodRepository periodRepository;
  private final EnrollmentApplicationCourseRepository selectionRepository;
  private final NamedParameterJdbcTemplate jdbc;

  @Transactional(propagation = Propagation.MANDATORY)
  public void configure(
      final EnrollmentPeriod period, final List<EnrollmentPeriodOfferingRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_REQUIRED);
    }
    final var byPlan = new LinkedHashMap<UUID, EnrollmentPeriodOfferingRequest>();
    for (final var request : requests) {
      if (request == null
          || request.studyPlanId() == null
          || request.academicLevelIds() == null
          || request.includeUnassigned() == null
          || request.academicLevelIds().stream().anyMatch(Objects::isNull)
          || new HashSet<>(request.academicLevelIds()).size() != request.academicLevelIds().size()
          || request.academicLevelIds().isEmpty() && !request.includeUnassigned()
          || byPlan.put(request.studyPlanId(), request) != null) {
        throw new EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_INVALID);
      }
    }
    if (period.isScopeConfigured()
        && applicationRepository.existsSubmittedByPeriod(period.getId())) {
      for (final var offering : period.getOfferings()) {
        final var requested = byPlan.get(offering.getStudyPlan().getId());
        if (requested == null
            || offering.getLevels().stream()
                .anyMatch(
                    level ->
                        level.levelId() == null
                            ? !requested.includeUnassigned()
                            : !requested.academicLevelIds().contains(level.levelId()))) {
          throw new EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_SHRINK);
        }
      }
    }

    final var prepared = new ArrayList<PreparedOffering>();
    for (final var request : requests) {
      final var plan =
          studyPlanRepository
              .findByIdAndInstitution_Id(request.studyPlanId(), period.getInstitution().getId())
              .filter(candidate -> candidate.getStatus() != StudyPlanStatus.DRAFT)
              .filter(
                  candidate ->
                      candidate.getTrainingPath().isActive()
                          && candidate.getTrainingPath().getDeletedAt() == null)
              .orElseThrow(
                  () -> new EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_INVALID));
      final var levels =
          academicLevelRepository.findByStudyPlan_IdOrderByDisplayOrderAsc(plan.getId()).stream()
              .filter(level -> request.academicLevelIds().contains(level.getId()))
              .toList();
      if (levels.size() != request.academicLevelIds().size()) {
        throw new EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_INVALID);
      }
      prepared.add(new PreparedOffering(plan, levels, request.includeUnassigned()));
    }

    if (period.isScopeConfigured()) {
      clearDraftSelections(period, byPlan);
    }
    period
        .getOfferings()
        .removeIf(offering -> !byPlan.containsKey(offering.getStudyPlan().getId()));
    for (final var selection : prepared) {
      final var plan = selection.plan();
      final var offering =
          period.getOfferings().stream()
              .filter(existing -> existing.getStudyPlan().getId().equals(plan.getId()))
              .findFirst()
              .orElseGet(
                  () -> {
                    final var created = EnrollmentPeriodOffering.create(period, plan);
                    period.getOfferings().add(created);
                    return created;
                  });
      offering.selectLevels(selection.levels(), selection.includeUnassigned());
    }
    period.markScopeConfigured();
  }

  private void clearDraftSelections(
      final EnrollmentPeriod period, final Map<UUID, EnrollmentPeriodOfferingRequest> desired) {
    final var removed =
        selectionRepository
            .findDraftsByPeriod(period.getInstitution().getId(), period.getId())
            .stream()
            .filter(
                selection -> {
                  final var space = selection.getCourse().getStudyPlanSpace();
                  final var requested = desired.get(space.getStudyPlan().getId());
                  return requested == null
                      || (space.getAcademicLevel() == null
                          ? !requested.includeUnassigned()
                          : !requested
                              .academicLevelIds()
                              .contains(space.getAcademicLevel().getId()));
                })
            .toList();
    if (removed.isEmpty()) {
      return;
    }

    // Delete through Hibernate before scope triggers run, so Envers sees each removed selection.
    selectionRepository.deleteAll(removed);
    selectionRepository.flush();
    // Preserve the trigger's timestamp-only side effect; this field is intentionally not audited.
    jdbc.update(
        "UPDATE enrollment_applications SET updated_at = CURRENT_TIMESTAMP WHERE enrollment_application_id IN (:ids)",
        Map.of(
            "ids",
            removed.stream()
                .map(value -> value.getEnrollmentApplication().getId())
                .distinct()
                .toList()));
  }

  private record PreparedOffering(
      StudyPlan plan, List<AcademicLevel> levels, boolean includeUnassigned) {}

  @Transactional(propagation = Propagation.MANDATORY)
  public EnrollmentPeriod save(final EnrollmentPeriod period) {
    try {
      // Removing scope rows also clears affected draft selections in the same transaction.
      return periodRepository.saveAndFlush(period);
    } catch (DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation) {
          final String constraint = violation.getConstraintName();
          if ("enrollment_period_scope_overlap".equals(constraint)) {
            throw new EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_OVERLAP);
          }
          if ("enrollment_period_scope_shrink".equals(constraint)) {
            throw new EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_SHRINK);
          }
          if (constraint != null && constraint.startsWith("enrollment_period_")) {
            throw new EnrollmentValidationException(EnrollmentMessages.PERIOD_SCOPE_INVALID);
          }
        }
      }
      throw exception;
    }
  }
}
