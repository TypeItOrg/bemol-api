package ar.edu.utn.frvm.typeit.boero_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassTeacher;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseClassTeacherRepository extends JpaRepository<CourseClassTeacher, UUID> {
  @EntityGraph(attributePaths = "person")
  List<CourseClassTeacher> findByCourseClass_IdIn(List<UUID> courseClassIds);

  @EntityGraph(attributePaths = "courseClass")
  List<CourseClassTeacher> findByInstitution_IdAndCourseClass_Course_IdIn(
      UUID institutionId, List<UUID> courseIds);

  boolean existsByCourseClass_Course_IdAndPerson_Id(UUID courseId, UUID personId);

  @Query(
      value =
          """
          SELECT courseClass FROM CourseClassTeacher assignment
          JOIN assignment.courseClass courseClass
          JOIN FETCH courseClass.course course
          JOIN FETCH course.studyPlanSpace placement
          JOIN FETCH placement.academicSpace
          LEFT JOIN FETCH course.instrument
          WHERE assignment.institution.id = :institutionId
            AND assignment.person.id = :personId
            AND course.deletedAt IS NULL
          ORDER BY courseClass.id
          """,
      countQuery =
          """
          SELECT COUNT(assignment) FROM CourseClassTeacher assignment
          WHERE assignment.institution.id = :institutionId
            AND assignment.person.id = :personId
            AND assignment.courseClass.course.deletedAt IS NULL
          """)
  Page<CourseClass> findAssignedClasses(
      @Param("institutionId") UUID institutionId,
      @Param("personId") UUID personId,
      Pageable pageable);

  @Query(
      """
      SELECT DISTINCT courseClass FROM CourseClassTeacher assignment
      JOIN assignment.courseClass courseClass
      JOIN FETCH courseClass.course course
      JOIN FETCH course.academicYear academicYear
      JOIN FETCH course.studyPlanSpace placement
      JOIN FETCH placement.academicSpace
      LEFT JOIN FETCH course.instrument
      WHERE assignment.institution.id = :institutionId
        AND assignment.person.id = :personId
        AND course.deletedAt IS NULL
        AND academicYear.startDate <= :weekEnd
        AND academicYear.endDate >= :weekStart
      ORDER BY courseClass.id
      """)
  List<CourseClass> findAssignedClassesInWeek(
      @Param("institutionId") UUID institutionId,
      @Param("personId") UUID personId,
      @Param("weekStart") LocalDate weekStart,
      @Param("weekEnd") LocalDate weekEnd);

  boolean existsByInstitution_IdAndPerson_IdAndCourseClass_Id(
      UUID institutionId, UUID personId, UUID classId);
}
