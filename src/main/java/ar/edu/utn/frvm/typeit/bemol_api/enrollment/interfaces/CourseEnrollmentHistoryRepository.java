package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseEnrollmentHistoryRepository
    extends JpaRepository<CourseEnrollmentHistory, UUID> {

  List<CourseEnrollmentHistory> findByCourseEnrollment_IdOrderByChangedAtDesc(
      UUID courseEnrollmentId);
}
