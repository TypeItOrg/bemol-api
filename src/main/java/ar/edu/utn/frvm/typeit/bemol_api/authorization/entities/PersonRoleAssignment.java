package ar.edu.utn.frvm.typeit.bemol_api.authorization.entities;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.PERSON_ROLE_INSTITUTION_MISMATCH;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.InvalidAccessScopeException;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
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
    name = "person_role_assignments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "person_role_assignments_unique",
            columnNames = {"person_id", "role_id", "institution_id"}),
    indexes = {
      @Index(
          name = "person_role_assignments_person_institution_idx",
          columnList = "person_id, institution_id"),
      @Index(
          name = "person_role_assignments_institution_role_idx",
          columnList = "institution_id, role_id")
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PersonRoleAssignment extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "person_role_assignment_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "person_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Person person;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Role role;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @Enumerated(EnumType.STRING)
  @Column(name = "access_scope", nullable = false)
  @Builder.Default
  @Audited
  private AccessScope accessScope = AccessScope.INSTITUTION;

  @ElementCollection
  @CollectionTable(
      name = "person_role_assignment_training_paths",
      joinColumns = @JoinColumn(name = "person_role_assignment_id"))
  @Column(name = "training_path_id", nullable = false)
  @Builder.Default
  @Audited
  private Set<UUID> trainingPathIds = new HashSet<>();

  public Set<UUID> getTrainingPathIds() {
    return Set.copyOf(trainingPathIds);
  }

  public void changeAccessScope(final AccessScope scope, final Set<UUID> paths) {
    if (scope == null
        || paths == null
        || (scope == AccessScope.INSTITUTION && !paths.isEmpty())
        || (scope == AccessScope.TRAINING_PATHS
            && (paths.isEmpty() || role.isInstitutionalAuthority()))) {
      throw new InvalidAccessScopeException();
    }
    accessScope = scope;
    var selected = Set.copyOf(paths);
    trainingPathIds.clear();
    trainingPathIds.addAll(selected);
  }

  public static PersonRoleAssignment assign(
      final Person person, final Role role, final Institution institution) {
    final PersonRoleAssignment assignment =
        PersonRoleAssignment.builder().person(person).role(role).institution(institution).build();
    assignment.validateInstitutionConsistency();
    return assignment;
  }

  public void replaceRole(final Role role) {
    this.role = role;
    validateInstitutionConsistency();
  }

  @PrePersist
  @PreUpdate
  private void validateInstitutionConsistency() {
    if (accessScope == null
        || trainingPathIds == null
        || (accessScope == AccessScope.INSTITUTION && !trainingPathIds.isEmpty())
        || (accessScope == AccessScope.TRAINING_PATHS
            && (trainingPathIds.isEmpty() || role.isInstitutionalAuthority()))) {
      throw new InvalidAccessScopeException();
    }
    final UUID institutionId = institution.getId();
    final UUID roleInstitutionId =
        role.getInstitution() == null ? null : role.getInstitution().getId();
    final boolean personMatches = institutionId.equals(person.getInstitution().getId());
    final boolean roleMatches =
        roleInstitutionId == null || institutionId.equals(roleInstitutionId);

    if (!personMatches || !roleMatches) {
      throw new IllegalStateException(PERSON_ROLE_INSTITUTION_MISMATCH);
    }
  }
}
