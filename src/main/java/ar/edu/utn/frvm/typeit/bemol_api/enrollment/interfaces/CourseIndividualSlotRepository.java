package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseIndividualSlot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseIndividualSlotRepository extends JpaRepository<CourseIndividualSlot, UUID> {

  List<CourseIndividualSlot> findBySchedule_IdOrderByStartTime(UUID scheduleId);

  List<CourseIndividualSlot> findBySchedule_IdIn(List<UUID> scheduleIds);

  @Modifying
  @Query(
      "DELETE FROM CourseIndividualSlot slot WHERE slot.schedule.day.courseClass.course.id = :courseId")
  void deleteByCourseId(@Param("courseId") UUID courseId);
}
