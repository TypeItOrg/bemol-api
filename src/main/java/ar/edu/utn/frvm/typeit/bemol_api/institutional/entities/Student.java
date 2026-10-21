package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@Table(
    name = "students",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "students_institution_id_id_unique",
          columnNames = {"institution_id", "student_id"}),
      @UniqueConstraint(
          name = "students_person_id_unique",
          columnNames = {"institution_id", "person_id"}),
      @UniqueConstraint(
          name = "students_file_number_unique",
          columnNames = {"institution_id", "file_number"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Student extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "student_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "person_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Person person;

  @Column(name = "file_number", length = 50)
  private String fileNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  @Builder.Default
  @Audited
  private StudentStatus status = StudentStatus.ACTIVE;

  @Column(name = "enrollment_date", nullable = false)
  @Audited
  private LocalDate enrollmentDate;
}
