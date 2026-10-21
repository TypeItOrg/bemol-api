package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseClassRepository extends JpaRepository<CourseClass, UUID> {
  List<CourseClass> findByCourse_IdOrderByClassNumberAsc(UUID courseId);

  List<CourseClass> findByInstitution_IdAndCourse_IdIn(UUID institutionId, List<UUID> courseIds);

  @Query(
      "SELECT courseClass FROM CourseClass courseClass WHERE courseClass.id = :id AND courseClass.course.id = :courseId AND courseClass.institution.id = :institutionId")
  Optional<CourseClass> findByIdAndCourseIdAndInstitutionId(
      @Param("id") UUID id,
      @Param("courseId") UUID courseId,
      @Param("institutionId") UUID institutionId);

  void deleteByCourse_Id(UUID courseId);
}
