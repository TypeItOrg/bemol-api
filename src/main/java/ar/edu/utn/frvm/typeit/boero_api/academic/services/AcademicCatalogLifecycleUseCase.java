package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleAction;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleResource;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.InstrumentNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicLifecycleRequest;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcademicCatalogLifecycleUseCase {
  private final Clock clock;
  private final AcademicYearRepository years;
  private final AcademicSpaceRepository spaces;
  private final InstrumentRepository instruments;
  private final ShiftRepository shifts;
  private final AcademicLifecycleJournal journal;

  @Transactional
  public void deleteAcademicYear(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    final var year =
        years
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(AcademicYearNotFoundException::new);
    if (year.delete(clock.instant())) {
      journal.record(
          year.getInstitution(),
          AcademicLifecycleResource.ACADEMIC_YEAR,
          id,
          AcademicLifecycleAction.DELETE,
          request);
    }
  }

  @Transactional
  public void restoreAcademicYear(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    final var year =
        years
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(AcademicYearNotFoundException::new);
    journal.restore(
        year, year.getInstitution(), AcademicLifecycleResource.ACADEMIC_YEAR, id, request);
  }

  @Transactional
  public void deleteAcademicSpace(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    final var space =
        spaces
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(AcademicSpaceNotFoundException::new);
    if (space.delete(clock.instant())) {
      journal.record(
          space.getInstitution(),
          AcademicLifecycleResource.ACADEMIC_SPACE,
          id,
          AcademicLifecycleAction.DELETE,
          request);
    }
  }

  @Transactional
  public void restoreAcademicSpace(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    final var space =
        spaces
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(AcademicSpaceNotFoundException::new);
    journal.restore(
        space, space.getInstitution(), AcademicLifecycleResource.ACADEMIC_SPACE, id, request);
  }

  @Transactional
  public void deleteInstrument(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    final var instrument =
        instruments
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(InstrumentNotFoundException::new);
    if (instrument.delete(clock.instant())) {
      journal.record(
          instrument.getInstitution(),
          AcademicLifecycleResource.INSTRUMENT,
          id,
          AcademicLifecycleAction.DELETE,
          request);
    }
  }

  @Transactional
  public void restoreInstrument(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    final var instrument =
        instruments
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(InstrumentNotFoundException::new);
    journal.restore(
        instrument, instrument.getInstitution(), AcademicLifecycleResource.INSTRUMENT, id, request);
  }

  @Transactional
  public void deleteShift(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    final var shift =
        shifts
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(ShiftNotFoundException::new);
    if (shift.delete(clock.instant())) {
      journal.record(
          shift.getInstitution(),
          AcademicLifecycleResource.SHIFT,
          id,
          AcademicLifecycleAction.DELETE,
          request);
    }
  }

  @Transactional
  public void restoreShift(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    final var shift =
        shifts
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(ShiftNotFoundException::new);
    journal.restore(shift, shift.getInstitution(), AcademicLifecycleResource.SHIFT, id, request);
  }
}
