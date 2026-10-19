package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@AuditOverride(forClass = SoftDeletable.class, name = "deletedAt", isAudited = true)
@Table(
    name = "academic_years",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "academic_years_institution_id_id_unique",
          columnNames = {"institution_id", "academic_year_id"})
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class AcademicYear extends SoftDeletable {

  public static final int MIN_YEAR = 2000;

  private static final int MAX_YEAR_OFFSET = 1;

  @Id
  @GeneratedUUIDv7
  @Column(name = "academic_year_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @Column(name = "\"year\"", nullable = false)
  @Audited
  private int year;

  @Column(name = "start_date")
  @Audited
  private @Nullable LocalDate startDate;

  @Column(name = "end_date")
  @Audited
  private @Nullable LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Audited
  private AcademicYearStatus status;

  public static AcademicYear create(
      final Institution institution,
      final int year,
      final @Nullable LocalDate startDate,
      final @Nullable LocalDate endDate,
      final LocalDate today) {
    validate(year, startDate, endDate, today);
    return AcademicYear.builder()
        .institution(institution)
        .year(year)
        .startDate(startDate)
        .endDate(endDate)
        .status(AcademicYearStatus.PLANNED)
        .build();
  }

  public void update(
      final int year,
      final @Nullable LocalDate startDate,
      final @Nullable LocalDate endDate,
      final LocalDate today) {
    if (status != AcademicYearStatus.PLANNED) {
      throw new InvalidAcademicStateException();
    }
    validate(year, startDate, endDate, today);
    this.year = year;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public void transitionTo(final AcademicYearStatus target) {
    if (target == status) {
      return;
    }
    final boolean validTransition =
        (status == AcademicYearStatus.PLANNED && target == AcademicYearStatus.ACTIVE)
            || (status == AcademicYearStatus.ACTIVE && target == AcademicYearStatus.CLOSED);
    if (!validTransition) {
      throw new InvalidAcademicStateException();
    }
    status = target;
  }

  public boolean delete(final Instant deletedAt) {
    if (status != AcademicYearStatus.PLANNED) {
      throw new InvalidAcademicStateException();
    }
    return markDeleted(deletedAt);
  }

  private static void validate(
      final int year,
      final @Nullable LocalDate startDate,
      final @Nullable LocalDate endDate,
      final LocalDate today) {
    validateYear(year, today);

    final boolean onlyOneDate = (startDate == null) != (endDate == null);
    if (onlyOneDate) {
      throw dateValidationError("endDate", AcademicMessages.DATE_PAIR_REQUIRED);
    }
    if (startDate == null || endDate == null) {
      return;
    }
    if (startDate.isAfter(endDate)) {
      throw dateValidationError("endDate", AcademicMessages.ACADEMIC_YEAR_DATES_INVALID);
    }
    if (startDate.getYear() != year) {
      throw dateValidationError("startDate", AcademicMessages.ACADEMIC_YEAR_START_DATE_INVALID);
    }
    final int endYear = endDate.getYear();
    if (endYear != year && endYear != year + 1) {
      throw dateValidationError("endDate", AcademicMessages.ACADEMIC_YEAR_END_DATE_INVALID);
    }
  }

  private static void validateYear(final int year, final LocalDate today) {
    final int maxYear = today.getYear() + MAX_YEAR_OFFSET;
    if (year < MIN_YEAR || year > maxYear) {
      throw new AcademicValidationException(
          AcademicMessages.ACADEMIC_YEAR_OUT_OF_RANGE,
          Map.of("year", AcademicMessages.ACADEMIC_YEAR_OUT_OF_RANGE));
    }
  }

  private static AcademicValidationException dateValidationError(
      final String field, final String message) {
    return new AcademicValidationException(
        AcademicMessages.ACADEMIC_YEAR_DATES_INVALID, Map.of(field, message));
  }
}
