package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleAction;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleResource;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicLifecycleRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcademicLifecycleService {
  private final AcademicCatalogLifecycleUseCase catalog;
  private final AcademicCurriculumLifecycleUseCase curriculum;
  private final AcademicLifecycleJournal journal;

  @Transactional
  public void deleteAcademicYear(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    catalog.deleteAcademicYear(institutionId, id, request);
  }

  @Transactional
  public void restoreAcademicYear(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    catalog.restoreAcademicYear(institutionId, id, request);
  }

  @Transactional
  public void deleteTrainingPath(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    curriculum.deleteTrainingPath(institutionId, id, request);
  }

  @Transactional
  public void restoreTrainingPath(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    curriculum.restoreTrainingPath(institutionId, id, request);
  }

  @Transactional
  public void deleteStudyPlan(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    curriculum.deleteStudyPlan(institutionId, id, request);
  }

  @Transactional
  public void restoreStudyPlan(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    curriculum.restoreStudyPlan(institutionId, id, request);
  }

  @Transactional
  public void recordStudyPlanVersionCreated(
      final Institution institution, final UUID versionId, final UUID previousVersionId) {
    journal.record(
        institution,
        AcademicLifecycleResource.STUDY_PLAN,
        versionId,
        AcademicLifecycleAction.CREATE_VERSION,
        new AcademicLifecycleRequest("Versión creada a partir del plan " + previousVersionId));
  }

  @Transactional
  public void deleteAcademicSpace(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    catalog.deleteAcademicSpace(institutionId, id, request);
  }

  @Transactional
  public void restoreAcademicSpace(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    catalog.restoreAcademicSpace(institutionId, id, request);
  }

  @Transactional
  public void deleteInstrument(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    catalog.deleteInstrument(institutionId, id, request);
  }

  @Transactional
  public void restoreInstrument(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    catalog.restoreInstrument(institutionId, id, request);
  }

  @Transactional
  public void deleteShift(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    catalog.deleteShift(institutionId, id, request);
  }

  @Transactional
  public void restoreShift(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    catalog.restoreShift(institutionId, id, request);
  }

  @Transactional
  public void deleteCourse(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    curriculum.deleteCourse(institutionId, id, request);
  }

  @Transactional
  public void restoreCourse(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    curriculum.restoreCourse(institutionId, id, request);
  }
}
