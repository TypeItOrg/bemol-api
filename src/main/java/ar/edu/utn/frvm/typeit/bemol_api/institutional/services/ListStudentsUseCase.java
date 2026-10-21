package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.StudentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.StudentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.student.StudentSummaryResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListStudentsUseCase {

  private final StudentRepository studentRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<StudentSummaryResponse> execute(
      final UUID institutionId, final @Nullable String search, final Pageable pageable) {
    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    final var students =
        normalizedSearch == null
            ? studentRepository.findByInstitution_IdAndStatusOrderByPerson_LastNameAsc(
                institutionId, StudentStatus.ACTIVE, pageable)
            : studentRepository.searchActive(
                institutionId, StudentStatus.ACTIVE, normalizedSearch, pageable);

    return PaginatedResponse.from(students.map(StudentSummaryResponse::from));
  }
}
