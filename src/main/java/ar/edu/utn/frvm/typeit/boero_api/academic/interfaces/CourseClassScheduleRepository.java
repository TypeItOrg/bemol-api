package ar.edu.utn.frvm.typeit.boero_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseClassScheduleRepository extends JpaRepository<CourseClassSchedule, UUID> {
  List<CourseClassSchedule> findByDay_IdIn(List<UUID> dayIds);

  void deleteByDay_CourseClass_Course_Id(UUID courseId);
}
