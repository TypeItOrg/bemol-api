package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpaceInstrument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPlanSpaceInstrumentRepository
    extends JpaRepository<StudyPlanSpaceInstrument, UUID> {

  void deleteByStudyPlanSpace_Id(UUID studyPlanSpaceId);

  @EntityGraph(attributePaths = "instrument")
  List<StudyPlanSpaceInstrument> findByStudyPlanSpace_IdOrderByInstrument_Name(
      UUID studyPlanSpaceId);

  @Query(
      """
      SELECT relation FROM StudyPlanSpaceInstrument relation
      JOIN FETCH relation.instrument instrument
      JOIN FETCH relation.studyPlanSpace space
      WHERE relation.institution.id = :institutionId
        AND space.studyPlan.id = :studyPlanId
      ORDER BY instrument.name, instrument.id
      """)
  List<StudyPlanSpaceInstrument> findByStudyPlanIdWithInstruments(
      @Param("institutionId") UUID institutionId, @Param("studyPlanId") UUID studyPlanId);

  @Query(
      """
      SELECT relation FROM StudyPlanSpaceInstrument relation
      JOIN FETCH relation.instrument instrument
      WHERE relation.institution.id = :institutionId
        AND relation.studyPlanSpace.id = :studyPlanSpaceId
        AND instrument.active = true
        AND instrument.deletedAt IS NULL
      ORDER BY instrument.name
      """)
  List<StudyPlanSpaceInstrument> findActiveByStudyPlanSpaceId(
      @Param("institutionId") UUID institutionId, @Param("studyPlanSpaceId") UUID studyPlanSpaceId);

  @Query(
      """
      SELECT relation FROM StudyPlanSpaceInstrument relation
      JOIN FETCH relation.instrument instrument
      WHERE relation.institution.id = :institutionId
        AND relation.studyPlanSpace.id IN :studyPlanSpaceIds
        AND instrument.active = true
        AND instrument.deletedAt IS NULL
      """)
  List<StudyPlanSpaceInstrument> findActiveByStudyPlanSpaceIds(
      @Param("institutionId") UUID institutionId,
      @Param("studyPlanSpaceIds") List<UUID> studyPlanSpaceIds);

  @Query(
      """
      SELECT COUNT(relation) > 0 FROM StudyPlanSpaceInstrument relation
      JOIN relation.instrument instrument
      WHERE relation.institution.id = :institutionId
        AND relation.studyPlanSpace.id = :studyPlanSpaceId
        AND instrument.id = :instrumentId
        AND instrument.active = true
        AND instrument.deletedAt IS NULL
      """)
  boolean existsActiveRelation(
      @Param("institutionId") UUID institutionId,
      @Param("studyPlanSpaceId") UUID studyPlanSpaceId,
      @Param("instrumentId") UUID instrumentId);
}
