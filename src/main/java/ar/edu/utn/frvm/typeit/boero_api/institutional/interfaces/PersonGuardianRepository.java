package ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonGuardianRepository extends JpaRepository<PersonGuardian, UUID> {

  @EntityGraph(attributePaths = "dependentPerson")
  List<PersonGuardian> findByInstitution_IdAndTutorPerson_IdOrderByCreatedAtAsc(
      UUID institutionId, UUID tutorPersonId);

  @EntityGraph(attributePaths = "dependentPerson")
  Optional<PersonGuardian> findByInstitution_IdAndTutorPerson_IdAndDependentPerson_Id(
      UUID institutionId, UUID tutorPersonId, UUID dependentPersonId);

  boolean existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_Id(
      UUID institutionId, UUID tutorPersonId, UUID dependentPersonId);

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
