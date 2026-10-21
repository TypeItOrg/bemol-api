package ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.StudentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, UUID> {

  boolean existsByInstitution_IdAndPerson_Id(UUID institutionId, UUID personId);

  Optional<Student> findByInstitution_IdAndPerson_Id(UUID institutionId, UUID personId);

  Optional<Student> findByIdAndInstitution_Id(UUID studentId, UUID institutionId);

  @EntityGraph(attributePaths = "person")
  Page<Student> findByInstitution_IdAndStatusOrderByPerson_LastNameAsc(
      UUID institutionId, StudentStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "person")
  @Query(
      "SELECT student FROM Student student JOIN student.person person WHERE student.institution.id = :institutionId AND student.status = :status AND (LOWER(person.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(person.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(person.documentNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(COALESCE(student.fileNumber, '')) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY person.lastName, person.firstName")
  Page<Student> searchActive(
      @Param("institutionId") UUID institutionId,
      @Param("status") StudentStatus status,
      @Param("search") String search,
      Pageable pageable);

  long countByInstitution_Id(UUID institutionId);

  @Query(value = "SELECT nextval('student_file_number_seq')", nativeQuery = true)
  long nextFileNumberSequenceValue();
}
