package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CountCoursesForYearUseCase {
  private final CourseRepository courseRepository;
  private final AcademicYearRepository academicYearRepository;

  @Transactional(readOnly = true)
  public long execute(final UUID institutionId, final UUID academicYearId) {
    academicYearRepository
        .findByIdAndInstitution_Id(academicYearId, institutionId)
        .orElseThrow(AcademicYearNotFoundException::new);
    return courseRepository.countByInstitutionIdAndAcademicYearIdAndStatusNot(
        institutionId, academicYearId, CourseStatus.CLOSED);
  }
}
