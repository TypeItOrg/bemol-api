package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@Table(
    name = "study_plan_space_instruments",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "study_plan_space_instruments_institution_id_id_unique",
          columnNames = {"institution_id", "study_plan_space_instrument_id"}),
      @UniqueConstraint(
          name = "study_plan_space_instruments_space_instrument_unique",
          columnNames = {"study_plan_space_id", "instrument_id"})
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class StudyPlanSpaceInstrument extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "study_plan_space_instrument_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "study_plan_space_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private StudyPlanSpace studyPlanSpace;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "instrument_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Instrument instrument;

  public static StudyPlanSpaceInstrument create(
      final Institution institution,
      final StudyPlanSpace studyPlanSpace,
      final Instrument instrument) {
    return StudyPlanSpaceInstrument.builder()
        .institution(institution)
        .studyPlanSpace(studyPlanSpace)
        .instrument(instrument)
        .build();
  }
}
