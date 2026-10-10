package ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonGuardianRepository extends JpaRepository<PersonGuardian, UUID> {

  @EntityGraph(attributePaths = "dependentPerson")
  List<PersonGuardian> findByInstitution_IdAndTutorPerson_IdAndStatusInOrderByCreatedAtAsc(
      UUID institutionId, UUID tutorPersonId, Collection<GuardianLinkStatus> statuses);

  @EntityGraph(attributePaths = {"tutorPerson", "dependentPerson"})
  List<PersonGuardian> findByInstitution_IdAndStatusOrderByCreatedAtAsc(
      UUID institutionId, GuardianLinkStatus status);

  @EntityGraph(attributePaths = {"institution", "tutorPerson", "dependentPerson"})
  List<PersonGuardian> findByStatusOrderByCreatedAtAsc(GuardianLinkStatus status);

  @EntityGraph(attributePaths = "dependentPerson")
  Optional<PersonGuardian> findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
      UUID institutionId, UUID tutorPersonId, UUID dependentPersonId, GuardianLinkStatus status);

  @EntityGraph(attributePaths = "tutorPerson")
  Optional<PersonGuardian> findByIdAndInstitution_Id(UUID id, UUID institutionId);

  /** Locks the link so two reviewers cannot resolve the same request concurrently. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"tutorPerson", "dependentPerson"})
  @Query(
      "SELECT link FROM PersonGuardian link "
          + "WHERE link.id = :id AND link.institution.id = :institutionId")
  Optional<PersonGuardian> findForUpdate(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  boolean existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
      UUID institutionId,
      UUID tutorPersonId,
      UUID dependentPersonId,
      Collection<GuardianLinkStatus> statuses);

  @Query(
      "SELECT new ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.DependentApplicationCount("
          + "application.applicantPerson.id, COUNT(application)) "
          + "FROM EnrollmentApplication application "
          + "WHERE application.institution.id = :institutionId "
          + "AND application.applicantPerson.id IN :personIds "
          + "AND application.deletedAt IS NULL "
          + "AND application.status NOT IN ("
          + "ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus.CANCELLED, "
          + "ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus.REJECTED) "
          + "GROUP BY application.applicantPerson.id")
  List<DependentApplicationCount> countActiveApplicationsByApplicant(
      @Param("institutionId") UUID institutionId, @Param("personIds") Collection<UUID> personIds);
}
