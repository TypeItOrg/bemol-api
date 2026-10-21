package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PersonAccessInvitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonAccessInvitationRepository
    extends JpaRepository<PersonAccessInvitation, UUID> {
  Optional<PersonAccessInvitation> findByTokenHash(String tokenHash);

  List<PersonAccessInvitation> findByPerson_Id(UUID personId);

  @EntityGraph(attributePaths = "representation")
  @Query(
      """
      select i from PersonAccessInvitation i
      where i.person.id in :personIds and i.person.institution.id = :institutionId
        and i.consumedAt is null and i.expiresAt > :now
      """)
  List<PersonAccessInvitation> findPendingByPersonIds(
      @Param("personIds") List<UUID> personIds,
      @Param("institutionId") UUID institutionId,
      @Param("now") Instant now);

  @Query(
      """
      select i.person.id from PersonAccessInvitation i
      where i.person.id in :personIds and i.institutionalIssuer is not null
        and i.consumedAt is null and i.expiresAt > :now
      """)
  List<UUID> findPendingInstitutionalPersonIds(
      @Param("personIds") List<UUID> personIds, @Param("now") Instant now);
}
