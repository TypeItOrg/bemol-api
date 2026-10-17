package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpaceInstrument;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceInstrumentRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateStudyPlanSpaceRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.StudyPlanSpaceInstrumentOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.StudyPlanSpaceResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateStudyPlanSpaceUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final AcademicSpaceRepository academicSpaceRepository;
  private final AcademicLevelRepository academicLevelRepository;
  private final InstrumentRepository instrumentRepository;
  private final StudyPlanSpaceInstrumentRepository studyPlanSpaceInstrumentRepository;
  private final StudyPlanDraftGuard studyPlanDraftGuard;

  @Transactional
  public StudyPlanSpaceResponse execute(
      final UUID institutionId, final UUID studyPlanId, final CreateStudyPlanSpaceRequest request) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE,
        institutionId,
        ScopedResource.STUDY_PLAN,
        studyPlanId);

    final var plan = studyPlanDraftGuard.lock(institutionId, studyPlanId);
    final var space =
        academicSpaceRepository
            .findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
                request.academicSpaceId(), institutionId)
            .orElseThrow(AcademicSpaceNotFoundException::new);
    final var level = resolveLevel(studyPlanId, request.academicLevelId());
    final var instruments = resolveInstruments(institutionId, request.instrumentIds());

    try {
      final var saved =
          studyPlanSpaceRepository.save(
              StudyPlanSpace.create(
                  plan.getInstitution(),
                  plan,
                  space,
                  level,
                  request.requirementType(),
                  request.displayOrder(),
                  request.approvalMode()));
      saveInstrumentRelations(saved, instruments);
      studyPlanSpaceRepository.flush();

      auditRecorder.recordCurrentActor(
          saved.getInstitution(),
          null,
          AuditAction.STUDY_PLAN_SPACE_CREATED,
          AuditEntityType.STUDY_PLAN_SPACE,
          saved.getId(),
          AuditOrigin.administration(),
          Map.of(
              "entityName",
              space.getName(),
              "studyPlanName",
              plan.getName(),
              "after",
              AcademicAuditDetails.space(
                  saved, instruments.stream().map(instrument -> instrument.getId()).toList())));
      return StudyPlanSpaceResponse.from(saved, instrumentOptions(instruments));
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }

  private @Nullable AcademicLevel resolveLevel(
      final UUID studyPlanId, final @Nullable UUID academicLevelId) {
    if (academicLevelId == null) {
      return null;
    }

    return academicLevelRepository
        .findByIdAndStudyPlan_Id(academicLevelId, studyPlanId)
        .orElseThrow(() -> new AcademicConflictException(AcademicMessages.INVALID_RELATIONSHIP));
  }

  private List<Instrument> resolveInstruments(
      final UUID institutionId, final List<UUID> instrumentIds) {
    if (instrumentIds == null || instrumentIds.isEmpty()) {
      return List.of();
    }

    final var uniqueIds = new LinkedHashSet<>(instrumentIds);

    if (uniqueIds.size() != instrumentIds.size()) {
      throw new AcademicConflictException(AcademicMessages.INVALID_RELATIONSHIP);
    }

    final List<Instrument> instruments = new ArrayList<>();

    for (final UUID instrumentId : uniqueIds) {
      final var instrument =
          instrumentRepository
              .findByIdAndInstitution_Id(instrumentId, institutionId)
              .filter(mappedInstrument -> mappedInstrument.isActive())
              .orElseThrow(
                  () -> new AcademicConflictException(AcademicMessages.INVALID_RELATIONSHIP));
      instruments.add(instrument);
    }

    return instruments;
  }

  private void saveInstrumentRelations(
      final StudyPlanSpace studyPlanSpace, final List<Instrument> instruments) {
    for (final Instrument instrument : instruments) {
      studyPlanSpaceInstrumentRepository.save(
          StudyPlanSpaceInstrument.create(
              studyPlanSpace.getInstitution(), studyPlanSpace, instrument));
    }
  }

  private List<StudyPlanSpaceInstrumentOptionResponse> instrumentOptions(
      final List<Instrument> instruments) {
    return instruments.stream()
        .map(
            instrument ->
                new StudyPlanSpaceInstrumentOptionResponse(
                    instrument.getId(), instrument.getName()))
        .toList();
  }
}
