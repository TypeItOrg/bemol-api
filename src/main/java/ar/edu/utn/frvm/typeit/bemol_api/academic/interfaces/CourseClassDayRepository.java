package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassDay;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseClassDayRepository extends JpaRepository<CourseClassDay, UUID> {
  List<CourseClassDay> findByCourseClass_IdIn(List<UUID> courseClassIds);

  void deleteByCourseClass_Course_Id(UUID courseId);
}
