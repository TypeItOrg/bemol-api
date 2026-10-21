package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Prerequisite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrerequisiteRepository extends JpaRepository<Prerequisite, UUID> {
  @org.springframework.data.jpa.repository.EntityGraph(
      attributePaths = {
        "targetStudyPlanSpace",
        "requiredStudyPlanSpace",
        "requiredStudyPlanSpace.academicSpace"
      })
  List<Prerequisite> findByTargetStudyPlanSpace_IdIn(java.util.Collection<UUID> targetIds);

  void deleteByStudyPlan_Id(UUID studyPlanId);

  List<Prerequisite> findByStudyPlan_Id(UUID studyPlanId);

  List<Prerequisite> findByTargetStudyPlanSpace_Id(UUID targetId);

  @Query(
      "SELECT prerequisite FROM Prerequisite prerequisite WHERE prerequisite.id = :id AND prerequisite.studyPlan.institution.id = :institutionId AND prerequisite.studyPlan.deletedAt IS NULL")
  Optional<Prerequisite> findByIdAndStudyPlan_Institution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  boolean existsByTargetStudyPlanSpace_IdOrRequiredStudyPlanSpace_Id(UUID id, UUID requiredId);
}
