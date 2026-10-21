package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
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

public interface UserRepository extends JpaRepository<User, UUID> {
  @Query(
      """
      SELECT user FROM User user
      WHERE user.person.documentNumber = :documentNumber
        AND user.institution.id = :institutionId
        AND user.person.deleted = false
      """)
  Optional<User> findByPersonDocumentNumberAndInstitution_Id(
      @Param("documentNumber") String documentNumber, @Param("institutionId") UUID institutionId);

  @EntityGraph(attributePaths = {"person", "institution"})
  @Query(
      """
      SELECT user FROM User user
      WHERE user.person.documentNumber = :documentNumber
        AND user.institution.id = :institutionId
        AND user.person.deleted = false
      """)
  Optional<User> findWithPersonAndInstitutionByPersonDocumentNumberAndInstitution_Id(
      @Param("documentNumber") String documentNumber, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"person", "institution"})
  @Query(
      """
      SELECT user FROM User user
      WHERE user.person.documentNumber = :documentNumber
        AND user.institution.id = :institutionId
        AND user.person.deleted = false
      """)
  Optional<User> findWithPersonAndInstitutionForPasswordRecovery(
      @Param("documentNumber") final String documentNumber,
      @Param("institutionId") final UUID institutionId);

  @Query(
      """
      SELECT COUNT(user) > 0 FROM User user
      WHERE user.person.documentNumber = :documentNumber
        AND user.institution.id = :institutionId
        AND user.person.deleted = false
      """)
  boolean existsByPersonDocumentNumberAndInstitution_Id(
      @Param("documentNumber") String documentNumber, @Param("institutionId") UUID institutionId);

  Optional<User> findByPerson_IdAndInstitution_Id(UUID personId, UUID institutionId);

  List<User> findByPerson_IdInAndInstitution_Id(List<UUID> personIds, UUID institutionId);

  @EntityGraph(attributePaths = {"person", "institution"})
  Optional<User> findWithPersonAndInstitutionById(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT user FROM User user WHERE user.id = :id")
  Optional<User> findWithLockById(@Param("id") UUID id);

  @EntityGraph(attributePaths = {"person", "institution"})
  @Query(
      """
      SELECT user FROM User user
      WHERE user.person.documentNumber = :documentNumber
        AND user.institution.id = :institutionId
        AND user.person.deleted = false
      """)
  List<User> findAllByPersonDocumentNumberAndInstitution_Id(
      @Param("documentNumber") String documentNumber, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"person", "institution"})
  @Query("select u from User u where u.id = :id")
  Optional<User> findForEmailVerificationById(@Param("id") UUID id);

  @Query(
      "select u.id from User u where u.person.documentNumber = :document and u.institution.id = :institutionId and u.person.deleted = false")
  Optional<UUID> findActiveUserId(
      @Param("document") String document, @Param("institutionId") UUID institutionId);

  Optional<User> findByWebauthnUserHandle(byte[] webauthnUserHandle);

  @Query(
      """
      select u.institution.id as institutionId, count(u) as userCount
      from User u
      where u.enabled = true and u.institution.id in :ids
      group by u.institution.id
      """)
  List<InstitutionUserCount> countEnabledUsersByInstitutionIdIn(@Param("ids") Collection<UUID> ids);

  @Query(
      """
      select count(u)
      from User u
      where u.enabled = true
        and u.person.deleted = false
        and u.institution.active = true
      """)
  long countUsersWithAccess();
}
